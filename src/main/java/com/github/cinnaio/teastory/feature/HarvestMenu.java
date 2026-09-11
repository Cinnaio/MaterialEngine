package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.config.HarvestToolsConfig;
import com.github.cinnaio.teastory.i18n.TeaStoryLang;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class HarvestMenu implements Listener {
    private static final int PAGE_SIZE = 36;
    private final JavaPlugin plugin;
    private final HarvestStats stats;
    private final TeaStoryLang lang;
    private final HarvestMenuItems items;
    private final Supplier<HarvestToolsConfig> config;
    private final Set<Inventory> open = ConcurrentHashMap.newKeySet();

    public HarvestMenu(JavaPlugin plugin, HarvestStats stats, TeaStoryLang lang, HarvestMenuItems items,
                       Supplier<HarvestToolsConfig> config) {
        this.plugin = plugin;
        this.stats = stats;
        this.lang = lang;
        this.items = items;
        this.config = config;
    }

    public void open(Player player, UUID target, String name) {
        if (!HarvestAccess.read(player, target)) {
            player.sendMessage(lang.text(player, "command.no-permission"));
            return;
        }
        Holder holder = new Holder(player.getUniqueId(), target, name);
        holder.inventory = Bukkit.createInventory(holder, 54, text(player, "title"));
        render(player, holder);
        open.add(holder.inventory);
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        if (!HarvestAccess.read(player, holder.target)) {
            holder.inventory.clear();
            close(player);
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (slot == 49) { close(player); return; }
        if (slot == 0 || slot == 2 || slot == 4 || slot == 6) {
            holder.tab = Tab.values()[slot / 2];
            holder.page = 0;
        } else if (slot >= 46 && slot <= 48) {
            holder.period = HarvestPeriod.values()[slot - 46];
            holder.page = 0;
        } else if (slot == 45) holder.page = Math.max(0, holder.page - 1);
        else if (slot == 53) holder.page = Math.min(holder.pages - 1, holder.page + 1);
        else if (slot != 50) return;
        render(player, holder);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.getInventory().clear();
            open.remove(event.getInventory());
        }
    }

    public void shutdown() {
        open.forEach(Inventory::clear);
        open.clear();
    }

    private void close(Player player) {
        player.getScheduler().run(plugin, task -> player.closeInventory(), null);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        HarvestStats.Summary total = stats.summary(holder.target, holder.period);
        List<Row> rows = rows(player, holder, total);
        holder.pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.clamp(holder.page, 0, holder.pages - 1);
        String[] icons = {"minecraft:book", "minecraft:wheat", "minecraft:iron_hoe", "minecraft:wheat_seeds"};
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            inventory.setItem(i * 2, items.create(icons[i], text(player, "tab." + tab.id), List.of(), tab == holder.tab));
        }
        List<Component> state = new ArrayList<>();
        state.add(HarvestMenuItems.text(lang.text(player, "harvest.period." + holder.period.id())));
        if (!stats.enabled()) state.add(HarvestMenuItems.text(lang.text(player, "harvest.stats.disabled")));
        if (!stats.persistenceReady()) state.add(HarvestMenuItems.text(lang.text(player, "harvest.stats.persistence-unavailable")));
        inventory.setItem(8, items.create("minecraft:player_head", Component.text(holder.name), state, false));
        if (rows.isEmpty()) inventory.setItem(22, items.create("minecraft:barrier", text(player, "empty"), List.of(), false));
        for (int i = holder.page * PAGE_SIZE; i < Math.min(rows.size(), (holder.page + 1) * PAGE_SIZE); i++) {
            Row row = rows.get(i);
            inventory.setItem(9 + i % PAGE_SIZE, items.create(row.icon(), row.name(), row.lore(), false));
        }
        if (holder.page > 0) button(player, inventory, 45, "minecraft:arrow", "previous");
        if (holder.page + 1 < holder.pages) button(player, inventory, 53, "minecraft:arrow", "next");
        for (HarvestPeriod period : HarvestPeriod.values()) {
            inventory.setItem(46 + period.ordinal(), items.create(period == HarvestPeriod.ALL ? "minecraft:book"
                            : period == HarvestPeriod.TODAY ? "minecraft:sunflower" : "minecraft:clock",
                    HarvestMenuItems.text(lang.text(player, "harvest.period." + period.id())), List.of(), period == holder.period));
        }
        button(player, inventory, 49, "minecraft:barrier", "close");
        button(player, inventory, 50, "minecraft:compass", "refresh");
        inventory.setItem(51, items.create("minecraft:paper", HarvestMenuItems.text(lang.text(player, "harvest.menu.page")
                .replace("{page}", Integer.toString(holder.page + 1)).replace("{pages}", Integer.toString(holder.pages))), List.of(), false));
    }

    private List<Row> rows(Player player, Holder holder, HarvestStats.Summary total) {
        if (holder.tab == Tab.OVERVIEW) {
            return List.of(metric(player, "minecraft:wheat_seeds", "harvests", total.harvests()),
                    metric(player, "minecraft:wheat", "items", total.items()),
                    metric(player, "minecraft:chest", "collected", total.collectedItems()),
                    metric(player, "minecraft:hopper", "dropped", total.droppedItems()),
                    metric(player, "minecraft:diamond", "quality", total.qualityItems()),
                    metric(player, "minecraft:gold_nugget", "bonus", total.bonusItems()));
        }
        if (holder.tab == Tab.PRODUCTS) {
            return stats.items(holder.target, Integer.MAX_VALUE, holder.period).stream().map(row -> new Row(row.item(), items.name(row.item()),
                    lore(player, new HarvestStats.Summary(0, row.output().items(), row.output().collected(), row.output().dropped(),
                            row.output().quality(), row.output().bonus()), false))).toList();
        }
        Map<String, HarvestStats.Summary> groups = new HashMap<>();
        for (var row : stats.breakdown(holder.target, Integer.MAX_VALUE, holder.period)) {
            groups.merge(holder.tab == Tab.TOOLS ? row.tool() : row.crop(), row.totals(), HarvestStats.Summary::add);
        }
        HarvestToolsConfig settings = config.get();
        return groups.entrySet().stream().sorted(Comparator.<Map.Entry<String, HarvestStats.Summary>>comparingLong(entry -> entry.getValue().harvests())
                .reversed().thenComparing(Map.Entry::getKey)).map(entry -> {
                    String id = entry.getKey();
                    String icon = holder.tab == Tab.TOOLS ? id : settings.crops().containsKey(id) ? settings.crops().get(id).seed()
                            : settings.fruits().getOrDefault(id, id);
                    Component name = holder.tab == Tab.CROPS ? HarvestMenuItems.text(lang.text(player, "harvest.crop-names." + id)) : items.name(icon);
                    if (holder.tab == Tab.CROPS && lang.text(player, "harvest.crop-names." + id).equals("harvest.crop-names." + id)) name = items.name(icon);
                    return new Row(icon, name, lore(player, entry.getValue()));
                }).toList();
    }

    private Row metric(Player player, String icon, String key, long value) {
        return new Row(icon, text(player, "metric." + key), List.of(HarvestMenuItems.text("§f" + value)));
    }

    private List<Component> lore(Player player, HarvestStats.Summary total) {
        return lore(player, total, true);
    }

    private List<Component> lore(Player player, HarvestStats.Summary total, boolean harvests) {
        List<Component> lines = new ArrayList<>();
        if (harvests) lines.add(line(player, "harvests", total.harvests()));
        lines.addAll(List.of(line(player, "items", total.items()),
                line(player, "collected", total.collectedItems()), line(player, "dropped", total.droppedItems()),
                line(player, "quality", total.qualityItems()), line(player, "bonus", total.bonusItems())));
        return lines;
    }

    private Component line(Player player, String key, long value) {
        return text(player, "metric." + key).append(Component.text(": " + value));
    }

    private Component text(Player player, String key) { return HarvestMenuItems.text(lang.text(player, "harvest.menu." + key)); }

    private void button(Player player, Inventory inventory, int slot, String icon, String key) {
        inventory.setItem(slot, items.create(icon, text(player, key), List.of(), false));
    }

    private record Row(String icon, Component name, List<Component> lore) { }
    private enum Tab {
        OVERVIEW("overview"), PRODUCTS("products"), TOOLS("tools"), CROPS("crops");
        final String id;
        Tab(String id) { this.id = id; }
    }

    private static final class Holder implements InventoryHolder {
        final UUID viewer;
        final UUID target;
        final String name;
        Inventory inventory;
        Tab tab = Tab.OVERVIEW;
        HarvestPeriod period = HarvestPeriod.ALL;
        int page;
        int pages = 1;
        Holder(UUID viewer, UUID target, String name) { this.viewer = viewer; this.target = target; this.name = name; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
