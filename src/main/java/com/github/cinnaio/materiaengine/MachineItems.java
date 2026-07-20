package com.github.cinnaio.materiaengine;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class MachineItems {
    private MachineItems() {
    }

    static boolean hasItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    static boolean canAccept(ItemStack current, ItemStack incoming) {
        return !hasItem(current) || current.isSimilar(incoming) && current.getAmount() + incoming.getAmount() <= current.getMaxStackSize();
    }

    static ItemStack createOutputItem(CraftEngineHook craftEngineHook, String id, int amount) {
        ItemStack custom = craftEngineHook.createItem(id);
        ItemStack output = custom != null ? custom : new ItemStack(materialFromId(id));
        output.setAmount(amount);
        return output;
    }

    static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    static Material materialFromId(String id) {
        String materialId = id == null ? "" : id.toUpperCase().replace("MINECRAFT:", "");
        Material material = Material.matchMaterial(materialId);
        return material == null ? Material.STONE : material;
    }
}
