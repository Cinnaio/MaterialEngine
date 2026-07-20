package com.github.cinnaio.materiaengine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TeaDryingPanGui implements Listener {
    private final JavaPlugin plugin;
    private final CraftEngineHook craftEngineHook;
    private final TeaDryingPanDataStore dataStore;
    private final NamespacedKey guiItemKey;
    private final Map<String, TeaDryingPanMachine> machines;
    private final Map<String, Inventory> openMachines = new HashMap<>();
    private final Map<String, Inventory> openStorages = new HashMap<>();
    private String blockId;
    private Component title;
    private int defaultProcessTicks;
    private int inputSlot;
    private int outputSlot;
    private List<Integer> progressSlots;
    private String progressLeftPrefix;
    private String progressMidPrefix;
    private String progressRightPrefix;
    private Map<String, TeaDryingPanRecipe> recipes = Map.of();
    private BukkitTask tickTask;

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    TeaDryingPanGui(JavaPlugin plugin, CraftEngineHook craftEngineHook, TeaDryingPanDataStore dataStore) {
        this.plugin = plugin;
        this.craftEngineHook = craftEngineHook;
        this.dataStore = dataStore;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        this.machines = dataStore.load();
        reload();
        startTicking();
    }

    void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("machines.tea-drying-pan");
        if (config == null) {
            throw new IllegalStateException("Missing machines.tea-drying-pan config");
        }

        this.blockId = config.getString("block-id", "cgap:tea_drying_pan");
        this.title = parseTitle(config.getString("title", "<shift:-11><image:cgap:tea_drying_pan_gui>炒茶（煮饭）锅"));
        this.defaultProcessTicks = config.getInt("process-ticks", 100);
        this.inputSlot = config.getInt("input-slot", 11);
        this.outputSlot = config.getInt("output-slot", 15);
        this.progressSlots = config.getIntegerList("progress-slots");
        if (progressSlots.isEmpty()) {
            this.progressSlots = List.of(config.getInt("progress-slot", 13));
        }
        this.progressLeftPrefix = config.getString("progress-item-prefix.left", "cgap:tea_progress_left_");
        this.progressMidPrefix = config.getString("progress-item-prefix.mid", "cgap:tea_progress_mid_");
        this.progressRightPrefix = config.getString("progress-item-prefix.right", "cgap:tea_progress_right_");
        this.recipes = loadRecipes(config);
    }

    void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Inventory inventory : openMachines.values()) {
            syncMachine(inventory);
        }
        for (Inventory inventory : openStorages.values()) {
            syncStorage(inventory);
        }
        dataStore.save(machines.values());
        openMachines.clear();
        openStorages.clear();
    }

    void save() {
        dataStore.save(machines.values());
    }

    @EventHandler
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!craftEngineHook.isCustomBlock(event.getClickedBlock(), blockId)) {
            return;
        }

        event.setCancelled(true);
        TeaDryingPanMachine machine = machineAt(event.getClickedBlock().getLocation());
        if (event.getPlayer().isSneaking()) {
            openStorage(event.getPlayer(), machine);
            return;
        }
        openMachine(event.getPlayer(), machine);
    }

    @EventHandler
    void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        int topSize = event.getInventory().getSize();
        int slot = event.getRawSlot();
        if (event.isShiftClick()) {
            handleShiftClick(event, holder.machine);
            return;
        }
        if (slot >= topSize) {
            return;
        }
        if (slot == outputSlot) {
            if (hasItem(event.getCursor())) {
                event.setCancelled(true);
            }
            return;
        }
        if (slot == inputSlot) {
            if (hasItem(event.getCursor()) && !isAllowedInput(event.getCursor())) {
                event.setCancelled(true);
                message(event.getWhoClicked(), "这里只能放入炒茶原料。");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> tryAutoStart(holder.machine, event.getInventory()));
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && slot != inputSlot) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getRawSlots().contains(inputSlot) && !isAllowedInput(event.getOldCursor())) {
            event.setCancelled(true);
            message(event.getWhoClicked(), "这里只能放入炒茶原料。");
            return;
        }
        if (event.getRawSlots().contains(inputSlot)) {
            Bukkit.getScheduler().runTask(plugin, () -> tryAutoStart(((Holder) event.getInventory().getHolder()).machine, event.getInventory()));
        }
    }

    @EventHandler
    void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            syncMachine(event.getInventory());
            openMachines.remove(holder.machine.key());
            save();
            return;
        }
        if (event.getInventory().getHolder() instanceof StorageHolder holder) {
            syncStorage(event.getInventory());
            openStorages.remove(holder.machine.key());
            save();
        }
    }

    private void openMachine(Player player, TeaDryingPanMachine machine) {
        Inventory inventory = Bukkit.createInventory(new Holder(machine), TeaDryingPanMachine.SIZE, title);
        inventory.setItem(inputSlot, cloneItem(machine.contents()[inputSlot]));
        inventory.setItem(outputSlot, cloneItem(machine.contents()[outputSlot]));
        render(inventory, machine);
        openMachines.put(machine.key(), inventory);
        player.openInventory(inventory);
    }

    private void openStorage(Player player, TeaDryingPanMachine machine) {
        Inventory inventory = Bukkit.createInventory(new StorageHolder(machine), TeaDryingPanMachine.SIZE, Component.text("炒茶锅存储"));
        inventory.setContents(cloneItems(machine.storageContents()));
        openStorages.put(machine.key(), inventory);
        player.openInventory(inventory);
    }

    private void start(TeaDryingPanMachine machine, Inventory inventory, org.bukkit.command.CommandSender sender) {
        ItemStack input = inventory.getItem(inputSlot);
        TeaDryingPanRecipe recipe = findRecipe(input, Bukkit.getWorld(machine.worldId()));
        if (recipe == null) {
            if (sender != null) {
                message(sender, "当前原料和天气没有可用配方。");
            }
            return;
        }
        ItemStack output = createOutputItem(recipe);
        if (input.getAmount() < recipe.inputAmount()) {
            if (sender != null) {
                message(sender, "原料数量不足。");
            }
            return;
        }
        if (!canAccept(inventory.getItem(outputSlot), output)) {
            if (sender != null) {
                message(sender, "请先取出产物。");
            }
            return;
        }

        input.setAmount(input.getAmount() - recipe.inputAmount());
        if (input.getAmount() <= 0) {
            inventory.setItem(inputSlot, null);
        }
        machine.running(true);
        machine.elapsed(0);
        machine.runningRecipeId(recipe.id());
        syncMachine(inventory);
        render(inventory, machine);
        save();
    }

    private void tick() {
        boolean dirty = false;
        for (TeaDryingPanMachine machine : machines.values()) {
            if (!machine.running()) {
                continue;
            }
            TeaDryingPanRecipe recipe = recipes.get(machine.runningRecipeId());
            if (recipe == null) {
                machine.running(false);
                machine.runningRecipeId(null);
                dirty = true;
                continue;
            }
            machine.elapsed(machine.elapsed() + 1);
            Inventory openInventory = openMachines.get(machine.key());
            if (openInventory != null) {
                render(openInventory, machine);
            }
            if (machine.elapsed() < recipe.processTicks()) {
                continue;
            }
            machine.running(false);
            machine.elapsed(0);
            machine.runningRecipeId(null);
            addOutput(machine, recipe);
            if (openInventory != null) {
                openInventory.setItem(outputSlot, cloneItem(machine.contents()[outputSlot]));
                render(openInventory, machine);
            }
            dirty = true;
        }
        if (dirty) {
            save();
        }
    }

    private void startTicking() {
        // ponytail: one loop is enough for one machine type; split per-region only if Folia load proves it matters.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void addOutput(TeaDryingPanMachine machine, TeaDryingPanRecipe recipe) {
        ItemStack output = createOutputItem(recipe);
        ItemStack current = machine.contents()[outputSlot];
        if (!hasItem(current)) {
            machine.contents()[outputSlot] = output;
            return;
        }
        current.setAmount(current.getAmount() + output.getAmount());
    }

    private void handleShiftClick(InventoryClickEvent event, TeaDryingPanMachine machine) {
        event.setCancelled(true);
        if (event.getRawSlot() < event.getInventory().getSize()) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (!isAllowedInput(current)) {
            return;
        }
        ItemStack input = event.getInventory().getItem(inputSlot);
        if (!canAccept(input, current)) {
            return;
        }
        int moved = moveOneStack(current, input, event.getInventory());
        current.setAmount(current.getAmount() - moved);
        if (current.getAmount() <= 0) {
            event.setCurrentItem(null);
        }
        syncMachine(event.getInventory());
        render(event.getInventory(), machine);
        tryAutoStart(machine, event.getInventory());
    }

    private void tryAutoStart(TeaDryingPanMachine machine, Inventory inventory) {
        if (!machine.running()) {
            start(machine, inventory, null);
        }
    }

    private int moveOneStack(ItemStack source, ItemStack input, Inventory inventory) {
        int space = !hasItem(input) ? source.getMaxStackSize() : input.getMaxStackSize() - input.getAmount();
        int moved = Math.min(source.getAmount(), space);
        if (!hasItem(input)) {
            ItemStack copy = source.clone();
            copy.setAmount(moved);
            inventory.setItem(inputSlot, copy);
            return moved;
        }
        input.setAmount(input.getAmount() + moved);
        return moved;
    }

    private void render(Inventory inventory, TeaDryingPanMachine machine) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != inputSlot && i != outputSlot && !progressSlots.contains(i)) {
                inventory.clear(i);
            }
        }

        int totalTicks = recipes.getOrDefault(machine.runningRecipeId(), fallbackRecipe()).processTicks();
        int percent = totalTicks == 0 ? 100 : Math.min(100, machine.elapsed() * 100 / totalTicks);
        int filledPixels = machine.running() ? Math.max(1, percent * progressWidth() / 100) : 0;
        int usedPixels = 0;
        for (int i = 0; i < progressSlots.size(); i++) {
            int width = progressSegmentWidth(i);
            int segmentPixels = Math.max(0, Math.min(width, filledPixels - usedPixels));
            inventory.setItem(progressSlots.get(i), createProgressItem(i, segmentPixels));
            usedPixels += width;
        }
    }

    private void syncMachine(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Holder holder)) {
            return;
        }
        holder.machine.contents()[inputSlot] = cloneItem(inventory.getItem(inputSlot));
        holder.machine.contents()[outputSlot] = cloneItem(inventory.getItem(outputSlot));
    }

    private void syncStorage(Inventory inventory) {
        if (!(inventory.getHolder() instanceof StorageHolder holder)) {
            return;
        }
        ItemStack[] contents = cloneItems(inventory.getContents());
        System.arraycopy(contents, 0, holder.machine.storageContents(), 0, TeaDryingPanMachine.SIZE);
    }

    private TeaDryingPanMachine machineAt(Location location) {
        return machines.computeIfAbsent(TeaDryingPanMachine.key(location), ignored -> TeaDryingPanMachine.at(location));
    }

    private boolean isAllowedInput(ItemStack item) {
        String itemId = craftEngineHook.getItemId(item);
        return itemId != null && recipes.values().stream().anyMatch(recipe -> recipe.acceptsInput(itemId));
    }

    private TeaDryingPanRecipe findRecipe(ItemStack input, World world) {
        String itemId = craftEngineHook.getItemId(input);
        if (itemId == null) {
            return null;
        }
        return recipes.values().stream()
                .filter(recipe -> recipe.matches(itemId, world) && input.getAmount() >= recipe.inputAmount())
                .findFirst()
                .orElse(null);
    }

    private Map<String, TeaDryingPanRecipe> loadRecipes(ConfigurationSection config) {
        Map<String, TeaDryingPanRecipe> loaded = new LinkedHashMap<>();
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return loaded;
        }
        for (String id : recipesSection.getKeys(false)) {
            ConfigurationSection recipe = recipesSection.getConfigurationSection(id);
            if (recipe == null) {
                continue;
            }
            String inputId = recipe.getString("input.id", "");
            String outputId = recipe.getString("output.id", "");
            if (inputId.isBlank() || outputId.isBlank()) {
                continue;
            }
            ItemStack fallbackOutput = namedItem(
                    Material.matchMaterial(recipe.getString("output.material", "DRIED_KELP")),
                    recipe.getString("output.name", outputId)
            );
            loaded.put(id, new TeaDryingPanRecipe(
                    id,
                    inputId,
                    Math.max(1, recipe.getInt("input.amount", 1)),
                    recipe.getString("conditions.weather", "any").toLowerCase(),
                    recipe.getInt("process-ticks", defaultProcessTicks),
                    outputId,
                    fallbackOutput,
                    Math.max(1, recipe.getInt("output.amount", 1))
            ));
        }
        return loaded;
    }

    private TeaDryingPanRecipe fallbackRecipe() {
        return new TeaDryingPanRecipe("", "", 1, "any", defaultProcessTicks, "", new ItemStack(Material.AIR), 1);
    }

    private boolean canAccept(ItemStack current, ItemStack incoming) {
        return !hasItem(current) || current.isSimilar(incoming) && current.getAmount() + incoming.getAmount() <= current.getMaxStackSize();
    }

    private static boolean hasItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private ItemStack createOutputItem(TeaDryingPanRecipe recipe) {
        ItemStack custom = craftEngineHook.createItem(recipe.outputId());
        ItemStack output = custom != null ? custom : recipe.outputItem().clone();
        output.setAmount(recipe.outputAmount());
        return output;
    }

    private ItemStack createProgressItem(int index, int pixels) {
        if (pixels <= 0) {
            return null;
        }
        String prefix = index == 0 ? progressLeftPrefix : index == progressSlots.size() - 1 ? progressRightPrefix : progressMidPrefix;
        ItemStack custom = craftEngineHook.createItem(prefix + pixels);
        return custom != null ? custom : guiItem(Material.RED_STAINED_GLASS_PANE, "炒制进度");
    }

    private int progressWidth() {
        int width = 0;
        for (int i = 0; i < progressSlots.size(); i++) {
            width += progressSegmentWidth(i);
        }
        return width;
    }

    private int progressSegmentWidth(int index) {
        return index == 0 || index == progressSlots.size() - 1 ? 9 : 18;
    }

    private ItemStack guiItem(Material material, String name) {
        ItemStack item = namedItem(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[TeaDryingPanMachine.SIZE];
        for (int i = 0; i < Math.min(items.length, copy.length); i++) {
            copy[i] = cloneItem(items[i]);
        }
        return copy;
    }

    private static Component parseTitle(String title) {
        String parsed = title
                .replace("<shift:-11>", "")
                .replace("<shift:-8>", "")
                .replace("<image:cgap:tea_drying_pan_gui>", "섀")
                .replace("대", "섀");
        if (!parsed.contains("섀")) {
            parsed = "섀" + parsed;
        }
        return parsed.contains("<") ? MINI_MESSAGE.deserialize(parsed) : LEGACY_SERIALIZER.deserialize(parsed);
    }

    private static void message(org.bukkit.command.CommandSender target, String message) {
        target.sendMessage(ChatColor.GREEN + "[MateriaEngine] " + ChatColor.WHITE + message);
    }

    private final class Holder implements InventoryHolder {
        private final TeaDryingPanMachine machine;

        private Holder(TeaDryingPanMachine machine) {
            this.machine = machine;
        }

        @Override
        public Inventory getInventory() {
            return openMachines.get(machine.key());
        }
    }

    private final class StorageHolder implements InventoryHolder {
        private final TeaDryingPanMachine machine;

        private StorageHolder(TeaDryingPanMachine machine) {
            this.machine = machine;
        }

        @Override
        public Inventory getInventory() {
            return openStorages.get(machine.key());
        }
    }
}
