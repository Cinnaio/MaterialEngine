package com.github.cinnaio.materiaengine;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TeaDryingPanGui implements Listener {
    private final Plugin plugin;
    private final CraftEngineHook craftEngineHook;
    private final NamespacedKey guiItemKey;
    private final Set<Holder> holders = new HashSet<>();
    private String blockId;
    private String title;
    private int processTicks;
    private int inputSlot;
    private int progressSlot;
    private int outputSlot;
    private int startSlot;
    private Set<Material> allowedInputMaterials;
    private Set<String> allowedInputNames;
    private Set<String> allowedInputIds;
    private List<String> progressItemIds;
    private String outputId;
    private ItemStack outputItem;

    TeaDryingPanGui(MateriaEnginePlugin plugin) {
        this.plugin = plugin;
        this.craftEngineHook = new CraftEngineHook();
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        reload();
    }

    void reload() {
        plugin.saveDefaultConfig();
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            javaPlugin.reloadConfig();
        }
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("machines.tea-drying-pan");
        if (config == null) {
            throw new IllegalStateException("Missing machines.tea-drying-pan config");
        }

        this.blockId = config.getString("block-id", "cgap:tea_drying_pan");
        this.title = config.getString("title", "炒茶（煮饭）锅");
        this.processTicks = config.getInt("process-ticks", 100);
        this.inputSlot = config.getInt("input-slot", 11);
        this.progressSlot = config.getInt("progress-slot", 13);
        this.outputSlot = config.getInt("output-slot", 15);
        this.startSlot = config.getInt("start-slot", 22);
        this.allowedInputMaterials = loadMaterials(config.getStringList("allowed-input-materials"));
        this.allowedInputNames = new HashSet<>(config.getStringList("allowed-input-names"));
        this.allowedInputIds = new HashSet<>(config.getStringList("allowed-input-ids"));
        this.progressItemIds = config.getStringList("progress-items");
        this.outputId = config.getString("output.id", "cgap:green_tea_leaf");
        this.outputItem = namedItem(
                Material.matchMaterial(config.getString("output.material", "DRIED_KELP")),
                config.getString("output.name", "绿茶干叶")
        );
    }

    void shutdown() {
        for (Holder holder : new HashSet<>(holders)) {
            if (holder.task != null) {
                holder.task.cancel();
            }
        }
        holders.clear();
    }

    void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.inventory = inventory;
        holders.add(holder);
        render(inventory, false, 0);
        player.openInventory(inventory);
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
        open(event.getPlayer());
    }

    @EventHandler
    void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < event.getInventory().getSize() && slot != inputSlot && slot != outputSlot) {
            event.setCancelled(true);
        }
        if (slot != startSlot || holder.running) {
            return;
        }

        ItemStack input = event.getInventory().getItem(inputSlot);
        if (!isAllowedInput(input)) {
            message(event.getWhoClicked(), "请放入绿茶鲜叶或绿茶青叶。");
            return;
        }

        input.setAmount(input.getAmount() - 1);
        holder.running = true;
        holder.elapsed = 0;
        holder.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(event.getInventory(), holder), 1L, 1L);
    }

    @EventHandler
    void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            if (holder.task != null) {
                holder.task.cancel();
            }
            holders.remove(holder);
        }
    }

    private void tick(Inventory inventory, Holder holder) {
        holder.elapsed++;
        render(inventory, true, holder.elapsed);

        if (holder.elapsed < processTicks) {
            return;
        }

        holder.running = false;
        if (holder.task != null) {
            holder.task.cancel();
        }
        inventory.addItem(createOutputItem());
        render(inventory, false, processTicks);
    }

    private void render(Inventory inventory, boolean running, int elapsed) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != inputSlot && i != progressSlot && i != outputSlot && i != startSlot) {
                inventory.clear(i);
            }
        }

        int percent = processTicks == 0 ? 100 : Math.min(100, elapsed * 100 / processTicks);
        inventory.setItem(progressSlot, createProgressItem(percent));
        inventory.setItem(startSlot, guiItem(running ? Material.YELLOW_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE, running ? "炒制中" : "开始炒制"));
    }

    private boolean isAllowedInput(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (allowedInputMaterials.contains(item.getType())) {
            return true;
        }
        String customId = craftEngineHook.getItemId(item);
        if (customId != null && allowedInputIds.contains(customId)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && allowedInputNames.contains(ChatColor.stripColor(meta.getDisplayName()));
    }

    private ItemStack createOutputItem() {
        ItemStack custom = craftEngineHook.createItem(outputId);
        return custom != null ? custom : outputItem.clone();
    }

    private ItemStack createProgressItem(int percent) {
        if (!progressItemIds.isEmpty()) {
            int index = Math.min(progressItemIds.size() - 1, percent * progressItemIds.size() / 101);
            ItemStack custom = craftEngineHook.createItem(progressItemIds.get(index));
            if (custom != null) {
                return custom;
            }
        }
        return guiItem(Material.LIME_STAINED_GLASS_PANE, "进度 " + percent + "%");
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

    private static Set<Material> loadMaterials(List<String> names) {
        Set<Material> materials = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    private static void message(org.bukkit.command.CommandSender target, String message) {
        target.sendMessage(ChatColor.GREEN + "[MateriaEngine] " + ChatColor.WHITE + message);
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean running;
        private int elapsed;
        private BukkitTask task;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
