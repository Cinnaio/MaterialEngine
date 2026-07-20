package com.github.cinnaio.materiaengine;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.properties.Property;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

final class CraftEngineHook {
    private boolean apiMismatchLogged;

    boolean isEnabled() {
        var plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return plugin != null && plugin.isEnabled();
    }

    boolean isCustomBlock(Block block, String id) {
        String blockId = getBlockId(block);
        return blockId != null && blockId.equals(id);
    }

    String getBlockId(Block block) {
        if (block == null || !isEnabled()) {
            return null;
        }
        try {
            if (!CraftEngineBlocks.isCustomBlock(block)) {
                return null;
            }
            var state = CraftEngineBlocks.getCustomBlockState(block);
            var owner = state != null ? state.owner() : null;
            Key key = owner != null ? owner.keyOptional().map(resourceKey -> resourceKey.location()).orElse(null) : null;
            return key == null || Key.MINECRAFT_NAMESPACE.equals(key.namespace()) ? null : key.asString();
        } catch (Throwable error) {
            logApiMismatch(error);
            return null;
        }
    }

    boolean isCustomItem(ItemStack item, String id) {
        String itemId = getItemId(item);
        return itemId != null && itemId.equals(id);
    }

    String getItemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !isEnabled()) {
            return null;
        }
        try {
            if (!CraftEngineItems.isCustomItem(item)) {
                return null;
            }
            Key key = CraftEngineItems.getCustomItemId(item);
            return key == null || Key.MINECRAFT_NAMESPACE.equals(key.namespace()) ? null : key.asString();
        } catch (Throwable error) {
            logApiMismatch(error);
            return null;
        }
    }

    ItemStack createItem(String id) {
        if (id == null || id.isBlank() || !isEnabled()) {
            return null;
        }
        try {
            var custom = CraftEngineItems.byId(Key.ce(id));
            return custom == null || custom.isEmpty() ? null : custom.buildItemStack(ItemBuildContext.empty(), 1);
        } catch (Throwable error) {
            logApiMismatch(error);
            return null;
        }
    }

    boolean setBooleanState(Block block, String id, String propertyName, boolean value) {
        if (block == null || id == null || id.isBlank() || !isEnabled()) {
            return false;
        }
        try {
            var custom = CraftEngineBlocks.byId(Key.ce(id));
            if (custom == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Property<Boolean> property = (Property<Boolean>) custom.getProperty(propertyName);
            if (property == null) {
                return false;
            }
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || !id.equals(getBlockId(block))) {
                state = custom.defaultState();
            }
            if (Boolean.valueOf(value).equals(state.get(property))) {
                return true;
            }
            return CraftEngineBlocks.place(block.getLocation(), state.with(property, value), false);
        } catch (Throwable error) {
            logApiMismatch(error);
            return false;
        }
    }

    private void logApiMismatch(Throwable error) {
        if (apiMismatchLogged) {
            return;
        }
        apiMismatchLogged = true;
        Bukkit.getLogger().warning("[MateriaEngine] CraftEngine API mismatch; custom block/item integration disabled.");
        error.printStackTrace();
    }
}
