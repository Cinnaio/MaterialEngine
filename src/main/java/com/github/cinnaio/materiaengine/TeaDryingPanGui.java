package com.github.cinnaio.materiaengine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
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
    private String titleTemplate;
    private Component title;
    private int defaultProcessTicks;
    private int inputSlot;
    private int outputSlot;
    private String progressImagePrefix;
    private int progressImageWidth;
    private Map<String, TeaDryingPanRecipe> recipes = Map.of();
    private BukkitTask tickTask;

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int PROGRESS_CHAR_START = 0xE900;

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
        this.titleTemplate = config.getString("title", "<shift:-11><image:cgap:tea_drying_pan_gui>炒茶（煮饭）锅");
        this.defaultProcessTicks = config.getInt("process-ticks", 100);
        this.inputSlot = config.getInt("input-slot", 11);
        this.outputSlot = config.getInt("output-slot", 15);
        this.progressImagePrefix = config.getString("progress-image-prefix", "cgap:tea_progress_");
        this.progressImageWidth = config.getInt("progress-image-width", 108);
        this.title = parseTitle(titleWithProgress(0));
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
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(holder.machine, event.getInventory()));
            return;
        }
        if (slot == inputSlot) {
            if (hasItem(event.getCursor()) && !isAllowedInput(event.getCursor())) {
                event.setCancelled(true);
                message(event.getWhoClicked(), "这里只能放入炒茶原料。");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(holder.machine, event.getInventory()));
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
            Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(((Holder) event.getInventory().getHolder()).machine, event.getInventory()));
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
        openMachines.put(machine.key(), inventory);
        render(inventory, machine);
        player.openInventory(inventory);
    }

    private void openStorage(Player player, TeaDryingPanMachine machine) {
        Inventory inventory = Bukkit.createInventory(new StorageHolder(machine), TeaDryingPanMachine.SIZE, Component.text("炒茶锅存储"));
        inventory.setContents(cloneItems(machine.storageContents()));
        openStorages.put(machine.key(), inventory);
        player.openInventory(inventory);
    }

    private void start(TeaDryingPanMachine machine, Inventory inventory, org.bukkit.command.CommandSender sender) {
        syncMachine(inventory);
        if (!start(machine, sender)) {
            return;
        }
        inventory.setItem(inputSlot, cloneItem(machine.contents()[inputSlot]));
        render(inventory, machine);
    }

    private boolean start(TeaDryingPanMachine machine, org.bukkit.command.CommandSender sender) {
        ItemStack input = machine.contents()[inputSlot];
        TeaDryingPanRecipe recipe = findRecipe(input, Bukkit.getWorld(machine.worldId()));
        if (recipe == null) {
            if (sender != null) {
                message(sender, "当前原料和天气没有可用配方。");
            }
            return false;
        }
        ItemStack output = createOutputItem(recipe);
        if (input.getAmount() < recipe.inputAmount()) {
            if (sender != null) {
                message(sender, "原料数量不足。");
            }
            return false;
        }
        if (!canAccept(machine.contents()[outputSlot], output)) {
            if (sender != null) {
                message(sender, "请先取出产物。");
            }
            return false;
        }

        machine.running(true);
        machine.elapsed(0);
        machine.runningRecipeId(recipe.id());
        save();
        return true;
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
            spawnSmoke(machine);
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
            consumeInput(machine, recipe);
            addOutput(machine, recipe);
            start(machine, null);
            if (openInventory != null) {
                openInventory.setItem(inputSlot, cloneItem(machine.contents()[inputSlot]));
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

    private void spawnSmoke(TeaDryingPanMachine machine) {
        if (machine.elapsed() % 5 != 0) {
            return;
        }
        World world = Bukkit.getWorld(machine.worldId());
        if (world == null) {
            return;
        }
        Location location = machine.location(world).add(0.5, 1.05, 0.5);
        world.spawnParticle(Particle.SMOKE, location, 2, 0.25, 0.12, 0.25, 0.01);
    }

    private void consumeInput(TeaDryingPanMachine machine, TeaDryingPanRecipe recipe) {
        ItemStack input = machine.contents()[inputSlot];
        if (!hasItem(input)) {
            return;
        }
        input.setAmount(input.getAmount() - recipe.inputAmount());
        if (input.getAmount() <= 0) {
            machine.contents()[inputSlot] = null;
        }
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
        syncAndTryAutoStart(machine, event.getInventory());
    }

    private void syncAndTryAutoStart(TeaDryingPanMachine machine, Inventory inventory) {
        syncMachine(inventory);
        if (machine.running()) {
            TeaDryingPanRecipe recipe = recipes.get(machine.runningRecipeId());
            if (recipe == null || findRecipe(machine.contents()[inputSlot], Bukkit.getWorld(machine.worldId())) == null) {
                machine.running(false);
                machine.elapsed(0);
                machine.runningRecipeId(null);
                render(inventory, machine);
            }
            save();
            return;
        }
        start(machine, inventory, null);
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
            if (i != inputSlot && i != outputSlot) {
                inventory.clear(i);
            }
        }

        updateTitle(inventory, machine);
    }

    private void updateTitle(Inventory inventory, TeaDryingPanMachine machine) {
        int pixels = progressPixels(machine);
        Component newTitle = parseTitle(titleWithProgress(pixels));
        String legacyTitle = SECTION_SERIALIZER.serialize(newTitle);
        for (HumanEntity viewer : inventory.getViewers()) {
            viewer.getOpenInventory().setTitle(legacyTitle);
        }
    }

    private int progressPixels(TeaDryingPanMachine machine) {
        if (!machine.running()) {
            return 0;
        }
        int totalTicks = recipes.getOrDefault(machine.runningRecipeId(), fallbackRecipe()).processTicks();
        if (totalTicks == 0) {
            return progressImageWidth;
        }
        return Math.max(1, Math.min(progressImageWidth, machine.elapsed() * progressImageWidth / totalTicks));
    }

    private String titleWithProgress(int pixels) {
        return titleTemplate.replace("{progress}", progressChar(pixels));
    }

    private String progressChar(int pixels) {
        return new String(Character.toChars(PROGRESS_CHAR_START + pixels));
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
        String parsed = legacyToMiniMessage(title)
                .replace("<shift:-11>", "")
                .replace("<shift:-8>", "")
                .replace("<image:cgap:tea_drying_pan_gui>", "섀")
                .replace("대", "섀");
        if (!parsed.contains("섀")) {
            parsed = "섀" + parsed;
        }
        return parsed.contains("<") ? MINI_MESSAGE.deserialize(parsed) : LEGACY_SERIALIZER.deserialize(parsed);
    }

    private static String legacyToMiniMessage(String text) {
        return text
                .replace("&r", "<reset>")
                .replace("&f", "<white>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&0", "<black>")
                .replace("&a", "<green>")
                .replace("&c", "<red>")
                .replace("&e", "<yellow>");
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
