package com.github.cinnaio.materiaengine.data;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class SimpleMachine extends StoredMachine {
    public SimpleMachine(UUID worldId, int x, int y, int z) {
        super(worldId, x, y, z);
    }

    public SimpleMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        super(worldId, x, y, z, contents, running, elapsed, runningRecipeId);
    }

    public SimpleMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId, int burnTimeLeft) {
        super(worldId, x, y, z, contents, running, elapsed, runningRecipeId, burnTimeLeft);
    }

    public static SimpleMachine at(Location location) {
        return new SimpleMachine(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
