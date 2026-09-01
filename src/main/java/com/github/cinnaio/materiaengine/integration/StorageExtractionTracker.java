package com.github.cinnaio.materiaengine.integration;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Observes custom machine storage inventories without taking over Bukkit's
 * click handling. A next-tick reconciliation sees the player's post-click
 * inventory and records only item ids whose held amount increased.
 */
public final class StorageExtractionTracker {
    private final JavaPlugin plugin;
    private final CraftEngineHook craftEngineHook;
    private final BeaconEngineBridge beaconEngineBridge;
    private final Map<UUID, PendingExtraction> pending = new HashMap<>();

    public StorageExtractionTracker(JavaPlugin plugin, CraftEngineHook craftEngineHook,
                                    BeaconEngineBridge beaconEngineBridge) {
        this.plugin = plugin;
        this.craftEngineHook = craftEngineHook;
        this.beaconEngineBridge = beaconEngineBridge;
    }

    public void observeClick(InventoryClickEvent event, Set<Integer> blockedSlots) {
        if (event.isCancelled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isExtractionAction(event.getAction())) {
            return;
        }
        Set<String> candidateIds = candidateIds(event.getInventory(), blockedSlots, event);
        if (!candidateIds.isEmpty()) {
            scheduleReconciliation(event.getInventory(), player, candidateIds);
        }
    }

    public void observeDrag(InventoryDragEvent event, Set<Integer> blockedSlots) {
        if (event.isCancelled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean touchesExposedSlot = event.getRawSlots().stream()
                .anyMatch(slot -> slot >= 0 && slot < event.getInventory().getSize() && !blockedSlots.contains(slot));
        if (!touchesExposedSlot) {
            return;
        }
        Set<String> candidateIds = exposedItemIds(event.getInventory(), blockedSlots);
        if (!candidateIds.isEmpty()) {
            scheduleReconciliation(event.getInventory(), player, candidateIds);
        }
    }

    public void shutdown() {
        this.pending.clear();
    }

    private void scheduleReconciliation(Inventory inventory, Player player, Set<String> candidateIds) {
        UUID playerId = player.getUniqueId();
        PendingExtraction extraction = this.pending.computeIfAbsent(
                playerId, ignored -> new PendingExtraction(player, heldItemCounts(player)));
        extraction.candidateIds.addAll(candidateIds);
        if (extraction.scheduled) {
            return;
        }
        extraction.scheduled = true;
        Bukkit.getScheduler().runTask(this.plugin, () -> reconcile(playerId));
    }

    private void reconcile(UUID playerId) {
        PendingExtraction extraction = this.pending.remove(playerId);
        if (extraction == null || !extraction.player.isOnline()) {
            return;
        }
        Map<String, Long> after = heldItemCounts(extraction.player);
        for (String itemId : extraction.candidateIds) {
            long beforeAmount = extraction.beforeHeld.getOrDefault(itemId, 0L);
            long afterAmount = after.getOrDefault(itemId, 0L);
            long delta = afterAmount - beforeAmount;
            if (delta > 0) {
                this.beaconEngineBridge.recordItemObtained(extraction.player, itemId, delta);
            }
        }
    }

    private Set<String> candidateIds(Inventory inventory, Set<Integer> blockedSlots, InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            return exposedItemIds(inventory, blockedSlots);
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= inventory.getSize() || blockedSlots.contains(rawSlot)) {
            return Set.of();
        }
        String itemId = itemId(inventory.getItem(rawSlot));
        return itemId == null ? Set.of() : Set.of(itemId);
    }

    private Set<String> exposedItemIds(Inventory inventory, Set<Integer> blockedSlots) {
        Set<String> itemIds = new HashSet<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (blockedSlots.contains(slot)) {
                continue;
            }
            String itemId = itemId(inventory.getItem(slot));
            if (itemId != null) {
                itemIds.add(itemId);
            }
        }
        return itemIds;
    }

    private String itemId(ItemStack item) {
        return MachineItems.itemIdOf(this.craftEngineHook, item);
    }

    private Map<String, Long> heldItemCounts(Player player) {
        Map<String, Long> counts = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            add(counts, item);
        }
        add(counts, player.getItemOnCursor());
        return counts;
    }

    private void add(Map<String, Long> counts, ItemStack item) {
        String itemId = itemId(item);
        if (itemId != null) {
            counts.merge(itemId, (long) item.getAmount(), Long::sum);
        }
    }

    private boolean isExtractionAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    SWAP_WITH_CURSOR, COLLECT_TO_CURSOR, MOVE_TO_OTHER_INVENTORY -> true;
            default -> false;
        };
    }

    private static final class PendingExtraction {
        private final Player player;
        private final Map<String, Long> beforeHeld;
        private final Set<String> candidateIds = new HashSet<>();
        private boolean scheduled;

        private PendingExtraction(Player player, Map<String, Long> beforeHeld) {
            this.player = player;
            this.beforeHeld = beforeHeld;
        }
    }
}
