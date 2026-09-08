package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class HarvestMenuItems {
    private final CraftEngineHook hook;

    public HarvestMenuItems(CraftEngineHook hook) { this.hook = hook; }

    private ItemStack base(String id) {
        ItemStack item = hook.createItem(id);
        if (MachineItems.hasItem(item)) return item.clone();
        Material material = id.startsWith("minecraft:") ? Material.matchMaterial(id) : null;
        item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        if (material == null) {
            var meta = item.getItemMeta();
            meta.displayName(Component.text(id));
            item.setItemMeta(meta);
        }
        return item;
    }

    public Component name(String id) {
        ItemStack item = base(id);
        var meta = item.getItemMeta();
        if (meta.hasDisplayName()) return meta.displayName();
        if (meta.hasItemName()) return meta.itemName();
        return Component.translatable(item.translationKey());
    }

    public ItemStack create(String id, Component name, List<Component> lore, boolean selected) {
        ItemStack item = base(id);
        item.setAmount(1);
        var meta = item.getItemMeta();
        if (name != null) meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(selected);
        item.setItemMeta(meta);
        return item;
    }

    public static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
}
