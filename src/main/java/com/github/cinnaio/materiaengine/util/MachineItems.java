package com.github.cinnaio.materiaengine.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MachineItems {
    private MachineItems() {
    }

    public static boolean hasItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    public static boolean canAccept(ItemStack current, ItemStack incoming) {
        return !hasItem(current) || current.isSimilar(incoming) && current.getAmount() + incoming.getAmount() <= current.getMaxStackSize();
    }

    public static ItemStack createOutputItem(CraftEngineHook craftEngineHook, String id, int amount) {
        ItemStack custom = craftEngineHook.createItem(id);
        ItemStack output = custom != null ? custom : new ItemStack(materialFromId(id));
        output.setAmount(amount);
        return output;
    }

    public static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public static int burnTicks(Material material) {
        return switch (material) {
            case LAVA_BUCKET -> 20000;
            case COAL_BLOCK -> 16000;
            case BLAZE_ROD -> 2400;
            case COAL, CHARCOAL -> 1600;
            case BAMBOO -> 50;
            default -> material.isFuel() ? 300 : 0;
        };
    }

    public static ItemStack craftingRemainder(Material material) {
        Material remainder = material.getCraftingRemainingItem();
        return remainder == null || remainder.isAir() ? null : new ItemStack(remainder);
    }

    public static Material materialFromId(String id) {
        String materialId = id == null ? "" : id.toUpperCase().replace("MINECRAFT:", "");
        Material material = Material.matchMaterial(materialId);
        return material == null ? Material.STONE : material;
    }
}
