package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.BlockStateConfig;
import com.github.cinnaio.materiaengine.config.MachineGuiLayout;
import com.github.cinnaio.materiaengine.data.MachineDataStore;
import com.github.cinnaio.materiaengine.data.SimpleMachine;
import com.github.cinnaio.materiaengine.data.StoredMachine;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SimpleProcessingMachineGui implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int PROGRESS_CHAR_START = 0xE900;

    private final JavaPlugin plugin;
    private final CraftEngineHook craftEngineHook;
    private final MachineDataStore<SimpleMachine> dataStore;
    private final MateriaEngineLang lang;
    private final String configPath;
    private final String langPrefix;
    private final Map<String, SimpleMachine> machines;
    private final Map<String, Inventory> openMachines = new HashMap<>();
    private final Map<String, Inventory> openStorages = new HashMap<>();
    private final Map<String, Integer> renderedProgress = new HashMap<>();

    private String blockId;
    private BlockStateConfig blockState;
    private int defaultProcessTicks;
    private int inputSlot;
    private int outputSlot;
    private int progressImageWidth;
    private int progressCharStart;
    private int titleUpdateTicks;
    private String imageToken;
    private String titleTemplate;
    private Map<String, SimpleMachineRecipe> recipes = Map.of();
    private BukkitTask tickTask;

    public SimpleProcessingMachineGui(JavaPlugin plugin, CraftEngineHook craftEngineHook, MateriaEngineLang lang,
                               String configPath, String table, String description, String langPrefix) {
        this.plugin = plugin;
        this.craftEngineHook = craftEngineHook;
        this.lang = lang;
        this.configPath = configPath;
        this.langPrefix = langPrefix;
        this.dataStore = new MachineDataStore<>(plugin, table, description, row -> new SimpleMachine(
                row.worldId(), row.x(), row.y(), row.z(), row.contents(), row.running(), row.elapsed(), row.runningRecipeId()
        ));
        this.machines = dataStore.load();
        reload();
        startTicking();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(configPath);
        if (config == null) {
            throw new IllegalStateException("Missing " + configPath + " config");
        }
        this.blockId = config.getString("block.id", "");
        this.blockState = BlockStateConfig.load(config, "stage", "int", 0, 1, 1);
        this.defaultProcessTicks = integer(config, "processing.process-ticks", config.getInt("process-ticks", 100));
        this.inputSlot = integer(config, "inventory.input-slot", config.getInt("input-slot", 11));
        this.outputSlot = integer(config, "inventory.output-slot", config.getInt("output-slot", 15));
        MachineGuiLayout gui = MachineGuiLayout.load(config, "<image:cgap:tea_drying_pan_gui>", 5, 108);
        this.progressImageWidth = gui.progressImageWidth();
        this.progressCharStart = integer(config, "gui.progress-char-start", config.getInt("progress-char-start", PROGRESS_CHAR_START));
        this.titleUpdateTicks = Math.max(1, gui.titleUpdateTicks());
        this.imageToken = gui.imageToken();
        this.titleTemplate = gui.titleTemplate();
        this.recipes = loadRecipes(config);
        machines.values().forEach(this::updateBlockState);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Inventory inventory : openMachines.values()) {
            syncMachine(inventory);
        }
        for (Inventory inventory : openStorages.values()) {
            syncStorage(inventory);
        }
        save();
        openMachines.clear();
        openStorages.clear();
        renderedProgress.clear();
    }

    public void save() {
        dataStore.save(machines.values());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!craftEngineHook.isCustomBlock(event.getClickedBlock(), blockId)) {
            return;
        }
        event.setCancelled(true);
        SimpleMachine machine = machineAt(event.getClickedBlock().getLocation());
        if (event.getPlayer().isSneaking()) {
            openStorage(event.getPlayer(), machine);
            return;
        }
        openMachine(event.getPlayer(), machine);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (!craftEngineHook.isCustomBlock(event.getBlock(), blockId)) {
            return;
        }
        String key = StoredMachine.key(event.getBlock().getLocation());
        SimpleMachine machine = machines.remove(key);
        if (machine == null) {
            dataStore.delete(key);
            return;
        }
        Inventory openInventory = openMachines.get(key);
        if (openInventory != null) {
            syncMachine(openInventory);
        }
        Inventory openStorage = openStorages.get(key);
        if (openStorage != null) {
            syncStorage(openStorage);
        }
        closeInventory(openMachines.remove(key));
        closeInventory(openStorages.remove(key));
        dropStoredItems(event.getBlock().getLocation(), machine.contents());
        dataStore.delete(key);
    }

    @EventHandler
    void onClick(InventoryClickEvent event) {
        Holder holder = holder(event.getInventory());
        if (holder == null) {
            return;
        }
        if (holder.storage) {
            handleStorageClick(event);
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
            event.setCancelled(true);
            return;
        }
        if (slot == inputSlot) {
            if (event.getClick().isKeyboardClick()) {
                event.setCancelled(true);
                return;
            }
            if (MachineItems.hasItem(event.getCursor()) && !isAllowedInput(event.getCursor())) {
                event.setCancelled(true);
                message(event.getWhoClicked(), "input-only");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(holder.machine, event.getInventory()));
            return;
        }
        if (MachineItems.hasItem(event.getCursor()) || event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(holder.machine, event.getInventory()));
    }

    @EventHandler
    void onDrag(InventoryDragEvent event) {
        Holder holder = holder(event.getInventory());
        if (holder == null) {
            return;
        }
        if (holder.storage) {
            if (event.getRawSlots().contains(inputSlot)) {
                event.setCancelled(true);
            }
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && slot != inputSlot && MachineItems.hasItem(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getRawSlots().contains(inputSlot) && !isAllowedInput(event.getOldCursor())) {
            event.setCancelled(true);
            message(event.getWhoClicked(), "input-only");
            return;
        }
        if (event.getRawSlots().contains(inputSlot)) {
            Bukkit.getScheduler().runTask(plugin, () -> syncAndTryAutoStart(holder.machine, event.getInventory()));
        }
    }

    @EventHandler
    void onClose(InventoryCloseEvent event) {
        Holder holder = holder(event.getInventory());
        if (holder != null) {
            if (holder.storage) {
                syncStorage(event.getInventory());
                openStorages.remove(holder.machine.key());
            } else {
                syncMachine(event.getInventory());
                openMachines.remove(holder.machine.key());
                renderedProgress.remove(holder.machine.key());
            }
            save();
        }
    }

    private void openMachine(Player player, SimpleMachine machine) {
        Inventory inventory = Bukkit.createInventory(new Holder(machine, false), StoredMachine.SIZE, title(player, 0));
        inventory.setItem(inputSlot, MachineItems.cloneItem(machine.contents()[inputSlot]));
        openMachines.put(machine.key(), inventory);
        render(inventory, machine);
        player.openInventory(inventory);
    }

    private void openStorage(Player player, SimpleMachine machine) {
        Inventory inventory = Bukkit.createInventory(new Holder(machine, true), StoredMachine.SIZE, Component.text("物品栏"));
        fillStorage(inventory, machine);
        openStorages.put(machine.key(), inventory);
        player.openInventory(inventory);
    }

    private boolean start(SimpleMachine machine, org.bukkit.command.CommandSender sender) {
        ItemStack input = machine.contents()[inputSlot];
        SimpleMachineRecipe recipe = findRecipe(input);
        if (recipe == null) {
            if (sender != null) {
                message(sender, "no-recipe");
            }
            return false;
        }
        ItemStack output = recipe.createOutput(craftEngineHook);
        if (input.getAmount() < recipe.inputAmount()) {
            if (sender != null) {
                message(sender, "not-enough-input");
            }
            return false;
        }
        if (!canStore(machine, output)) {
            if (sender != null) {
                message(sender, "output-blocked");
            }
            return false;
        }
        machine.running(true);
        machine.elapsed(0);
        machine.runningRecipeId(recipe.id());
        updateBlockState(machine);
        save();
        return true;
    }

    private void tick() {
        boolean dirty = false;
        for (SimpleMachine machine : machines.values()) {
            if (!machine.running()) {
                continue;
            }
            SimpleMachineRecipe recipe = recipes.get(machine.runningRecipeId());
            if (recipe == null) {
                machine.running(false);
                machine.runningRecipeId(null);
                updateBlockState(machine);
                dirty = true;
                continue;
            }
            machine.elapsed(machine.elapsed() + 1);
            spawnParticle(machine);
            Inventory openInventory = openMachines.get(machine.key());
            if (openInventory != null) {
                updateTitle(openInventory, machine);
            }
            if (machine.elapsed() < recipe.processTicks()) {
                continue;
            }
            machine.running(false);
            machine.elapsed(0);
            machine.runningRecipeId(null);
            syncOpenStorage(machine);
            ItemStack output = recipe.createOutput(craftEngineHook);
            if (!canStore(machine, output)) {
                dirty = true;
                continue;
            }
            consumeInput(machine, recipe);
            store(machine.contents(), output);
            start(machine, null);
            updateBlockState(machine);
            refreshOpenStorage(machine);
            if (openInventory != null) {
                openInventory.setItem(inputSlot, MachineItems.cloneItem(machine.contents()[inputSlot]));
                render(openInventory, machine);
            }
            dirty = true;
        }
        if (dirty) {
            save();
        }
    }

    private void startTicking() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void spawnParticle(SimpleMachine machine) {
        if (machine.elapsed() % 10 != 0) {
            return;
        }
        World world = Bukkit.getWorld(machine.worldId());
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, machine.location(world).add(0.5, 1.0, 0.5), 1, 0.25, 0.15, 0.25, 0.01);
    }

    private void consumeInput(SimpleMachine machine, SimpleMachineRecipe recipe) {
        ItemStack input = machine.contents()[inputSlot];
        if (!MachineItems.hasItem(input)) {
            return;
        }
        input.setAmount(input.getAmount() - recipe.inputAmount());
        if (input.getAmount() <= 0) {
            machine.contents()[inputSlot] = null;
        }
    }

    private void addOutput(SimpleMachine machine, SimpleMachineRecipe recipe) {
        store(machine.contents(), recipe.createOutput(craftEngineHook));
    }

    private boolean canStore(SimpleMachine machine, ItemStack output) {
        for (int i = 0; i < StoredMachine.SIZE; i++) {
            if (i != inputSlot && MachineItems.canAccept(machine.contents()[i], output)) {
                return true;
            }
        }
        return false;
    }

    private void store(ItemStack[] contents, ItemStack output) {
        for (int i = 0; i < StoredMachine.SIZE; i++) {
            if (i != inputSlot && MachineItems.canAccept(contents[i], output)) {
                if (!MachineItems.hasItem(contents[i])) {
                    contents[i] = output;
                    return;
                }
                contents[i].setAmount(contents[i].getAmount() + output.getAmount());
                return;
            }
        }
    }

    private void handleStorageClick(InventoryClickEvent event) {
        int topSize = event.getInventory().getSize();
        int slot = event.getRawSlot();
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (slot == inputSlot || slot < topSize && event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
        }
    }

    private void syncStorage(Inventory inventory) {
        Holder holder = holder(inventory);
        if (holder == null || !holder.storage) {
            return;
        }
        for (int i = 0; i < StoredMachine.SIZE; i++) {
            if (i != inputSlot) {
                holder.machine.contents()[i] = MachineItems.cloneItem(inventory.getItem(i));
            }
        }
        updateBlockState(holder.machine);
    }

    private void fillStorage(Inventory inventory, SimpleMachine machine) {
        for (int i = 0; i < StoredMachine.SIZE; i++) {
            inventory.setItem(i, i == inputSlot ? null : MachineItems.cloneItem(machine.contents()[i]));
        }
    }

    private void syncOpenStorage(SimpleMachine machine) {
        Inventory storage = openStorages.get(machine.key());
        if (storage != null) {
            syncStorage(storage);
        }
    }

    private void refreshOpenStorage(SimpleMachine machine) {
        Inventory storage = openStorages.get(machine.key());
        if (storage != null) {
            fillStorage(storage, machine);
        }
    }

    private void handleShiftClick(InventoryClickEvent event, SimpleMachine machine) {
        event.setCancelled(true);
        if (event.getRawSlot() < event.getInventory().getSize()) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (!isAllowedInput(current)) {
            return;
        }
        ItemStack input = event.getInventory().getItem(inputSlot);
        if (!MachineItems.canAccept(input, current)) {
            return;
        }
        int moved = moveOneStack(current, input, event.getInventory());
        current.setAmount(current.getAmount() - moved);
        if (current.getAmount() <= 0) {
            event.setCurrentItem(null);
        }
        syncMachine(event.getInventory());
        syncAndTryAutoStart(machine, event.getInventory());
    }

    private int moveOneStack(ItemStack source, ItemStack input, Inventory inventory) {
        int space = !MachineItems.hasItem(input) ? source.getMaxStackSize() : input.getMaxStackSize() - input.getAmount();
        int moved = Math.min(source.getAmount(), space);
        if (!MachineItems.hasItem(input)) {
            ItemStack copy = source.clone();
            copy.setAmount(moved);
            inventory.setItem(inputSlot, copy);
            return moved;
        }
        input.setAmount(input.getAmount() + moved);
        return moved;
    }

    private void syncAndTryAutoStart(SimpleMachine machine, Inventory inventory) {
        syncMachine(inventory);
        if (machine.running()) {
            SimpleMachineRecipe recipe = recipes.get(machine.runningRecipeId());
            if (recipe == null || findRecipe(machine.contents()[inputSlot]) == null) {
                machine.running(false);
                machine.elapsed(0);
                machine.runningRecipeId(null);
                render(inventory, machine);
            }
            save();
            return;
        }
        start(machine, null);
    }

    private void syncMachine(Inventory inventory) {
        Holder holder = holder(inventory);
        if (holder == null) {
            return;
        }
        holder.machine.contents()[inputSlot] = MachineItems.cloneItem(inventory.getItem(inputSlot));
        updateBlockState(holder.machine);
    }

    private void render(Inventory inventory, SimpleMachine machine) {
        updateTitle(inventory, machine);
    }

    private void updateTitle(Inventory inventory, SimpleMachine machine) {
        int pixels = progressPixels(machine);
        Integer previous = renderedProgress.get(machine.key());
        if (previous != null && machine.running() && machine.elapsed() % titleUpdateTicks != 0) {
            return;
        }
        renderedProgress.put(machine.key(), pixels);
        for (HumanEntity viewer : inventory.getViewers()) {
            Component newTitle = title(viewer instanceof Player player ? player : null, pixels);
            viewer.getOpenInventory().setTitle(SECTION_SERIALIZER.serialize(newTitle));
        }
    }

    private int progressPixels(SimpleMachine machine) {
        if (!machine.running()) {
            return 0;
        }
        int totalTicks = recipes.getOrDefault(machine.runningRecipeId(), fallbackRecipe()).processTicks();
        if (totalTicks == 0) {
            return progressImageWidth;
        }
        return Math.max(1, Math.min(progressImageWidth, machine.elapsed() * progressImageWidth / totalTicks));
    }

    private void updateBlockState(SimpleMachine machine) {
        World world = Bukkit.getWorld(machine.worldId());
        if (world == null) {
            return;
        }
        int value = state(machine);
        if (blockState.booleanType()) {
            craftEngineHook.setBooleanState(machine.location(world).getBlock(), blockId, blockState.property(), value != 0);
            return;
        }
        craftEngineHook.setIntState(machine.location(world).getBlock(), blockId, blockState.property(), value);
    }

    private int state(SimpleMachine machine) {
        if (machine.running()) {
            return blockState.runningValue();
        }
        SimpleMachineRecipe outputRecipe = recipeByOutput(machine.contents());
        if (outputRecipe != null) {
            return outputRecipe.outputState();
        }
        return hasStoredItem(machine) ? blockState.filledValue() : blockState.defaultValue();
    }

    private boolean hasStoredItem(SimpleMachine machine) {
        for (ItemStack item : machine.contents()) {
            if (MachineItems.hasItem(item)) {
                return true;
            }
        }
        return false;
    }

    private SimpleMachineRecipe recipeByOutput(ItemStack[] items) {
        for (ItemStack item : items) {
            SimpleMachineRecipe recipe = recipeByOutput(item);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    private SimpleMachineRecipe recipeByOutput(ItemStack item) {
        String itemId = craftEngineHook.getItemId(item);
        if (itemId == null) {
            return null;
        }
        return recipes.values().stream().filter(recipe -> recipe.outputId().equals(itemId)).findFirst().orElse(null);
    }

    private void dropStoredItems(Location location, ItemStack[] items) {
        for (ItemStack item : items) {
            dropStoredItem(location, item);
        }
    }

    private void dropStoredItem(Location location, ItemStack item) {
        if (MachineItems.hasItem(item)) {
            location.getWorld().dropItemNaturally(location, item.clone());
        }
    }

    private SimpleMachine machineAt(Location location) {
        return machines.computeIfAbsent(StoredMachine.key(location), ignored -> SimpleMachine.at(location));
    }

    private boolean isAllowedInput(ItemStack item) {
        String itemId = craftEngineHook.getItemId(item);
        return itemId != null && recipes.values().stream().anyMatch(recipe -> recipe.acceptsInput(itemId));
    }

    private SimpleMachineRecipe findRecipe(ItemStack input) {
        String itemId = craftEngineHook.getItemId(input);
        if (itemId == null) {
            return null;
        }
        return recipes.values().stream()
                .filter(recipe -> recipe.matches(itemId) && input.getAmount() >= recipe.inputAmount())
                .findFirst()
                .orElse(null);
    }

    private Map<String, SimpleMachineRecipe> loadRecipes(ConfigurationSection config) {
        Map<String, SimpleMachineRecipe> loaded = new LinkedHashMap<>();
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            return loaded;
        }
        for (String id : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(id);
            if (recipeSection == null) {
                continue;
            }
            SimpleMachineRecipe recipe = SimpleMachineRecipe.load(id, recipeSection, defaultProcessTicks, blockState.filledValue());
            if (recipe != null) {
                loaded.put(id, recipe);
            }
        }
        return loaded;
    }

    private Component title(Player player, int pixels) {
        String template = titleTemplate.isBlank() ? lang.text(player, langPrefix + ".title") : titleTemplate;
        String title = template
                .replace("{image}", imageToken)
                .replace("{progress}", progressChar(pixels))
                .replace("{name}", lang.text(player, langPrefix + ".name"));
        return parseTitle(title);
    }

    private String progressChar(int pixels) {
        return new String(Character.toChars(progressCharStart + pixels));
    }

    private Component parseTitle(String title) {
        String parsed = legacyToMiniMessage(title)
                .replace("<shift:-11>", "")
                .replace("<shift:-8>", "");
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

    private static int integer(ConfigurationSection config, String path, int fallback) {
        return config.isInt(path) ? config.getInt(path) : fallback;
    }

    private SimpleMachineRecipe fallbackRecipe() {
        return new SimpleMachineRecipe("", "", 1, defaultProcessTicks, "", 1, blockState.filledValue());
    }

    private void message(org.bukkit.command.CommandSender target, String key) {
        target.sendMessage(lang.text(target, langPrefix + "." + key));
    }

    private Holder holder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Holder holder)) {
            return null;
        }
        Inventory expected = holder.storage ? openStorages.get(holder.machine.key()) : openMachines.get(holder.machine.key());
        return expected == inventory ? holder : null;
    }

    private void closeInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (HumanEntity viewer : inventory.getViewers().toArray(HumanEntity[]::new)) {
            viewer.closeInventory();
        }
    }

    private final class Holder implements InventoryHolder {
        private final SimpleMachine machine;
        private final boolean storage;

        private Holder(SimpleMachine machine, boolean storage) {
            this.machine = machine;
            this.storage = storage;
        }

        @Override
        public Inventory getInventory() {
            return storage ? openStorages.get(machine.key()) : openMachines.get(machine.key());
        }
    }
}
