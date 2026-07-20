package com.github.cinnaio.materiaengine;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class TeaDryingPanMachine extends StoredMachine {
    static final int SIZE = StoredMachine.SIZE;

    TeaDryingPanMachine(UUID worldId, int x, int y, int z) {
        super(worldId, x, y, z);
    }

    TeaDryingPanMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        super(worldId, x, y, z, contents, running, elapsed, runningRecipeId);
    }

    static TeaDryingPanMachine at(Location location) {
        return new TeaDryingPanMachine(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
