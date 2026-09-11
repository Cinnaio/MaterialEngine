package com.github.cinnaio.teastory.util;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.libraries.antigrieflib.Flag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class CraftEngineHook {
    private boolean apiMismatchLogged;
    private volatile HarvestAccess harvestAccess;

    public boolean isEnabled() {
        var plugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return plugin != null && plugin.isEnabled();
    }

    public boolean isCustomBlock(Block block, String id) {
        String blockId = getBlockId(block);
        return blockId != null && blockId.equals(id);
    }

    public String getBlockId(Block block) {
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

    public boolean isCustomItem(ItemStack item, String id) {
        String itemId = getItemId(item);
        return itemId != null && itemId.equals(id);
    }

    public String getItemId(ItemStack item) {
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

    public ItemStack createItem(String id) {
        if (id == null || id.isBlank() || !isEnabled()) {
            return null;
        }
        try {
            var custom = CraftEngineItems.byId(Key.ce(id));
            return custom == null ? null : custom.buildBukkitItem(ItemBuildContext.empty(), 1);
        } catch (Throwable error) {
            logApiMismatch(error);
            return null;
        }
    }

    public boolean setBooleanState(Block block, String id, String propertyName, boolean value) {
        return setState(block, id, propertyName, value);
    }

    public boolean setIntState(Block block, String id, String propertyName, int value) {
        return setState(block, id, propertyName, value);
    }

    public boolean isReady() {
        return isEnabled() && BukkitCraftEngine.instance().isFullyLoaded() && !BukkitCraftEngine.instance().isReloading();
    }

    public Integer getIntState(Block block, String propertyName) {
        return readState(block, propertyName, Integer.class);
    }

    public Boolean getBooleanState(Block block, String propertyName) {
        return readState(block, propertyName, Boolean.class);
    }

    private <T> T readState(Block block, String propertyName, Class<T> type) {
        if (!isReady()) return null;
        try {
            var state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return null;
            var property = state.getProperty(propertyName);
            Object value = property == null ? null : state.get(property);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (RuntimeException | LinkageError error) {
            logApiMismatch(error);
            return null;
        }
    }

    public boolean canHarvest(Player player, Block block, boolean replant) {
        if (!isReady()) return false;
        try {
            var protection = BukkitCraftEngine.instance().antiGriefProvider();
            var location = block.getLocation();
            return protection.test(player, Flag.INTERACT, location)
                    && protection.test(player, Flag.BREAK, location)
                    && (!replant || protection.test(player, Flag.PLACE, location));
        } catch (RuntimeException | LinkageError error) {
            logApiMismatch(error);
            return false;
        }
    }

    /** Null means cancelled or unavailable; an empty list preserves a plugin's no-drops decision. */
    public List<ItemStack> customHarvestDrops(Player player, Block block) {
        try {
            var state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return null;
            var world = BukkitAdaptor.adapt(block.getWorld());
            var serverPlayer = BukkitAdaptor.adapt(player);
            var context = ContextHolder.builder()
                    .withParameter(DirectContextParameters.POSITION,
                            new WorldPosition(world, block.getX() + .5, block.getY() + .5, block.getZ() + .5))
                    .withParameter(DirectContextParameters.PLAYER, serverPlayer)
                    .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, state)
                    .withParameter(DirectContextParameters.BLOCK, BukkitAdaptor.adapt(block));
            HarvestAccess access = harvestAccess;
            if (access == null) harvestAccess = access = HarvestAccess.resolve();
            CustomBlockBreakEvent event = access.holderContext()
                    ? access.event().newInstance(serverPlayer, block.getLocation(), block, state, true)
                    : access.event().newInstance(serverPlayer, block.getLocation(), block, state, true, context);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return null;
            if (!event.dropItems()) return new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Item> drops = (List<Item>) access.drops().invoke(state,
                    access.holderContext() ? context.build() : context, world, serverPlayer);
            List<ItemStack> result = new ArrayList<>();
            for (Item item : drops) result.add(((ItemStack) item.platformItem()).clone());
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logApiMismatch(error);
            return null;
        }
    }

    public boolean removeHarvestedBlock(Block block, Player player) {
        return CraftEngineBlocks.remove(block, player, false, false, true);
    }

    // CraftEngine 26.9 changed both the loot context and break-event constructor.
    private record HarvestAccess(Method drops, Constructor<CustomBlockBreakEvent> event, boolean holderContext) {
        static HarvestAccess resolve() throws ReflectiveOperationException {
            Class<?> world = net.momirealms.craftengine.core.world.World.class;
            Class<?> player = net.momirealms.craftengine.core.entity.player.Player.class;
            try {
                return new HarvestAccess(ImmutableBlockState.class.getMethod("getDrops", ContextHolder.class, world, player),
                        CustomBlockBreakEvent.class.getConstructor(BukkitServerPlayer.class, Location.class,
                                Block.class, ImmutableBlockState.class, boolean.class), true);
            } catch (NoSuchMethodException oldApi) {
                return new HarvestAccess(ImmutableBlockState.class.getMethod("getDrops", ContextHolder.Builder.class, world, player),
                        CustomBlockBreakEvent.class.getConstructor(BukkitServerPlayer.class, Location.class,
                                Block.class, ImmutableBlockState.class, boolean.class, ContextHolder.Builder.class), false);
            }
        }
    }

    private <T extends Comparable<T>> boolean setState(Block block, String id, String propertyName, T value) {
        if (block == null || id == null || id.isBlank() || !isEnabled()) {
            return false;
        }
        try {
            var custom = CraftEngineBlocks.byId(Key.ce(id));
            if (custom == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Property<T> property = (Property<T>) custom.getProperty(propertyName);
            if (property == null) {
                return false;
            }
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || !id.equals(getBlockId(block))) {
                state = custom.defaultState();
            }
            if (value.equals(state.get(property))) {
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
        Bukkit.getLogger().warning("[TeaStory] CraftEngine API mismatch; custom block/item integration disabled.");
        error.printStackTrace();
    }
}
