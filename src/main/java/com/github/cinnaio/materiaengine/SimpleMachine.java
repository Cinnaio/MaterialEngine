package com.github.cinnaio.materiaengine;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class SimpleMachine extends StoredMachine {
    SimpleMachine(UUID worldId, int x, int y, int z) {
        super(worldId, x, y, z);
    }

    SimpleMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        super(worldId, x, y, z, contents, running, elapsed, runningRecipeId);
    }

    static SimpleMachine at(Location location) {
        return new SimpleMachine(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
