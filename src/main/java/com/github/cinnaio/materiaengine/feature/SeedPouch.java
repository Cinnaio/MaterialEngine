package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SeedPouch implements Listener {
    private static final int[] BAG_SLOTS = java.util.stream.IntStream.concat(java.util.stream.IntStream.range(0, 36), java.util.stream.IntStream.of(40)).toArray();
    private final JavaPlugin plugin;
    private final CraftEngineHook hook;
    private final MateriaEngineLang lang;
    private final HarvestMenuItems menuItems;
    private final Supplier<HarvestToolsConfig> config;
    private final NamespacedKey contentsKey;
    private final NamespacedKey identityKey;
    private final Set<Inventory> menus = ConcurrentHashMap.newKeySet();

    public SeedPouch(JavaPlugin plugin, CraftEngineHook hook, MateriaEngineLang lang, HarvestMenuItems menuItems,
                     Supplier<HarvestToolsConfig> config) {
        this.plugin = plugin;
        this.hook = hook;
        this.lang = lang;
        this.menuItems = menuItems;
        this.config = config;
        contentsKey = new NamespacedKey(plugin, "seed_pouch_contents");
        identityKey = new NamespacedKey(plugin, "seed_pouch_identity");
    }

    private boolean isPouch(ItemStack item) {
        return MachineItems.hasItem(item) && hook.isCustomItem(item, config.get().pouch().item());
    }

    private boolean allowedSeed(ItemStack item) {
        if (!MachineItems.hasItem(item) || isPouch(item)) return false;
        String id = MachineItems.itemIdOf(hook, item);
        return config.get().crops().values().stream().anyMatch(crop -> crop.seed().equals(id));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
        Player player = event.getPlayer();
        if (!isPouch(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        open(player, player.getInventory().getHeldItemSlot());
    }

    public void openFirst(Player player) {
        for (int slot : BAG_SLOTS) {
            if (isPouch(player.getInventory().getItem(slot))) { open(player, slot); return; }
        }
        message(player, "missing");
    }

    private void open(Player player, int slot) {
        if (!HarvestAccess.read(player, player.getUniqueId())) { player.sendMessage(lang.text(player, "command.no-permission")); return; }
        if (!config.get().pouch().enabled()) { message(player, "disabled"); return; }
        Bag bag = read(player, slot);
        if (bag == null) { message(player, "unavailable"); return; }
        if (bag.identity() == null) {
            if (!save(player, bag, bag.contents())) return;
            bag = read(player, slot);
            if (bag == null) return;
        }
        Holder holder = new Holder(player.getUniqueId(), bag.identity());
        holder.inventory = Bukkit.createInventory(holder, bag.contents().length + 9, HarvestMenuItems.text(lang.text(player, "harvest.pouch.title")));
        render(player, holder, bag);
        menus.add(holder.inventory);
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (!HarvestAccess.read(player, player.getUniqueId()) || !config.get().pouch().enabled()) { close(player); return; }
        Bag bag = findBag(player, holder.identity);
        if (bag == null || bag.contents().length + 9 != holder.inventory.getSize()) { message(player, "unavailable"); close(player); return; }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        if (MachineItems.hasItem(event.getCursor())) return;
        int raw = event.getRawSlot();
        int capacity = bag.contents().length;
        if (raw == capacity + 8) { close(player); return; }
        if (raw >= 0 && raw < capacity) {
            withdraw(player, bag, raw, click == ClickType.RIGHT ? 1 : Integer.MAX_VALUE);
        } else if (raw == capacity + 2) {
            for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                Bag current = findBag(player, holder.identity);
                if (current == null) break;
                deposit(player, current, slot, Integer.MAX_VALUE);
            }
        } else if (raw == capacity + 4) {
            for (int slot = 0; slot < capacity; slot++) {
                Bag current = findBag(player, holder.identity);
                if (current == null) break;
                withdraw(player, current, slot, Integer.MAX_VALUE);
            }
        } else if (event.getClickedInventory() == player.getInventory() && event.getSlot() >= 0) {
            deposit(player, bag, event.getSlot(), click == ClickType.RIGHT ? 1 : Integer.MAX_VALUE);
        }
        Bag current = findBag(player, holder.identity);
        if (current != null) render(player, holder, current);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.getInventory().clear();
            menus.remove(event.getInventory());
        }
    }

    public void shutdown() { menus.forEach(Inventory::clear); menus.clear(); }

    public Seed findSeed(Player player, String id) {
        if (!config.get().pouch().enabled()) return null;
        for (int slot : BAG_SLOTS) {
            Bag bag = read(player, slot);
            if (bag == null) continue;
            for (int i = 0; i < bag.contents().length; i++) {
                if (id.equals(MachineItems.itemIdOf(hook, bag.contents()[i]))) return new Seed(slot, bag.snapshot(), i);
            }
        }
        return null;
    }

    public boolean consume(Player player, Seed source) {
        Bag bag = read(player, source.bagSlot());
        if (bag == null || !bag.snapshot().isSimilar(source.snapshot()) || source.index() < 0 || source.index() >= bag.contents().length) return false;
        ItemStack seed = bag.contents()[source.index()];
        if (!MachineItems.hasItem(seed)) return false;
        seed.setAmount(seed.getAmount() - 1);
        if (seed.getAmount() <= 0) bag.contents()[source.index()] = null;
        return save(player, bag, bag.contents());
    }

    int deposit(Player player, Bag bag, int slot, int maximum) {
        ItemStack original = player.getInventory().getItem(slot);
        if (slot == bag.slot() || !allowedSeed(original)) return 0;
        int moved = insert(original, bag.contents(), maximum);
        if (moved == 0 || !save(player, bag, bag.contents())) return 0;
        ItemStack remainder = original.clone();
        remainder.setAmount(original.getAmount() - moved);
        player.getInventory().setItem(slot, remainder.getAmount() > 0 ? remainder : null);
        return moved;
    }

    private static int insert(ItemStack original, ItemStack[] contents, int maximum) {
        int amount = Math.max(0, Math.min(original.getAmount(), maximum));
        int left = amount;
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack current = contents[i];
            boolean occupied = MachineItems.hasItem(current);
            if (pass == 0 && (!occupied || !current.isSimilar(original)) || pass == 1 && occupied) continue;
            int moved = Math.min(left, original.getMaxStackSize() - (occupied ? current.getAmount() : 0));
            if (moved <= 0) continue;
            if (!occupied) {
                current = original.clone();
                current.setAmount(moved);
                contents[i] = current;
            } else current.setAmount(current.getAmount() + moved);
            left -= moved;
        }
        return amount - left;
    }

    int withdraw(Player player, Bag bag, int slot, int maximum) {
        ItemStack original = bag.contents()[slot];
        if (!MachineItems.hasItem(original) || !matches(player, bag)) return 0;
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] updated = Arrays.stream(storage).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        int moved = insert(original, updated, maximum);
        if (moved <= 0) { message(player, "inventory-full"); return 0; }
        original.setAmount(original.getAmount() - moved);
        if (original.getAmount() <= 0) bag.contents()[slot] = null;
        // Persist the debit before giving seeds; never overwrite the newly saved pouch slot.
        if (!save(player, bag, bag.contents())) return 0;
        for (int i = 0; i < updated.length; i++) {
            if (i != bag.slot() && !java.util.Objects.equals(storage[i], updated[i])) player.getInventory().setItem(i, updated[i]);
        }
        return moved;
    }

    Bag read(Player player, int slot) {
        ItemStack actual = player.getInventory().getItem(slot);
        if (!isPouch(actual) || actual.getAmount() != 1) return null;
        try {
            ItemStack snapshot = actual.clone();
            var pdc = snapshot.getItemMeta().getPersistentDataContainer();
            byte[] bytes = pdc.get(contentsKey, PersistentDataType.BYTE_ARRAY);
            if (bytes != null && bytes.length > 1_048_576) return null;
            ItemStack[] contents = bytes == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(bytes);
            if (contents.length > 27) return null;
            contents = Arrays.copyOf(contents, Math.max(config.get().pouch().slots(), ((contents.length + 8) / 9) * 9));
            for (ItemStack item : contents) {
                if (MachineItems.hasItem(item) && (isPouch(item) || item.getAmount() > item.getMaxStackSize())) return null;
            }
            return new Bag(slot, snapshot, pdc.get(identityKey, PersistentDataType.STRING), contents);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Bag findBag(Player player, String identity) {
        for (int slot : BAG_SLOTS) {
            Bag bag = read(player, slot);
            if (bag != null && identity.equals(bag.identity())) return bag;
        }
        return null;
    }

    private boolean matches(Player player, Bag bag) {
        ItemStack actual = player.getInventory().getItem(bag.slot());
        return MachineItems.hasItem(actual) && actual.getAmount() == 1 && actual.isSimilar(bag.snapshot());
    }

    private boolean save(Player player, Bag bag, ItemStack[] contents) {
        if (!matches(player, bag)) return false;
        try {
            byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
            if (bytes.length > 1_048_576) return false;
            ItemStack updated = bag.snapshot().clone();
            var meta = updated.getItemMeta();
            meta.getPersistentDataContainer().set(identityKey, PersistentDataType.STRING,
                    bag.identity() == null ? UUID.randomUUID().toString() : bag.identity());
            meta.getPersistentDataContainer().set(contentsKey, PersistentDataType.BYTE_ARRAY, bytes);
            if (!updated.setItemMeta(meta)) return false;
            player.getInventory().setItem(bag.slot(), updated);
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Failed to save seed pouch: " + error.getMessage());
            message(player, "unavailable");
            return false;
        }
    }

    private void render(Player player, Holder holder, Bag bag) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        int count = 0;
        for (int i = 0; i < bag.contents().length; i++) {
            ItemStack seed = bag.contents()[i];
            if (!MachineItems.hasItem(seed)) continue;
            count += seed.getAmount();
            inventory.setItem(i, seed.clone());
        }
        int base = bag.contents().length;
        inventory.setItem(base, menuItems.create(config.get().pouch().item(), HarvestMenuItems.text(lang.text(player, "harvest.pouch.title")),
                List.of(HarvestMenuItems.text(lang.text(player, "harvest.pouch.capacity").replace("{slots}", Integer.toString(base))
                        .replace("{count}", Integer.toString(count)))), false));
        button(player, inventory, base + 2, "minecraft:hopper", "deposit");
        button(player, inventory, base + 4, "minecraft:chest", "withdraw");
        button(player, inventory, base + 8, "minecraft:barrier", "close");
    }

    private void button(Player player, Inventory inventory, int slot, String id, String key) {
        inventory.setItem(slot, menuItems.create(id, HarvestMenuItems.text(lang.text(player, "harvest.pouch." + key)), List.of(), false));
    }

    private void message(Player player, String key) { player.sendMessage(lang.text(player, "harvest.pouch." + key)); }
    private void close(Player player) { player.getScheduler().run(plugin, task -> player.closeInventory(), null); }

    public record Seed(int bagSlot, ItemStack snapshot, int index) { }
    record Bag(int slot, ItemStack snapshot, String identity, ItemStack[] contents) { }
    private static final class Holder implements InventoryHolder {
        final UUID owner;
        final String identity;
        Inventory inventory;
        Holder(UUID owner, String identity) { this.owner = owner; this.identity = identity; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
