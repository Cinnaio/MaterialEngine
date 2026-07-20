package com.github.cinnaio.materiaengine.data;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class TeaDryingPanMachine extends StoredMachine {
    public static final int SIZE = StoredMachine.SIZE;

    public TeaDryingPanMachine(UUID worldId, int x, int y, int z) {
        super(worldId, x, y, z);
    }

    public TeaDryingPanMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        super(worldId, x, y, z, contents, running, elapsed, runningRecipeId);
    }

    public static TeaDryingPanMachine at(Location location) {
        return new TeaDryingPanMachine(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
