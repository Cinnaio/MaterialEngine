package com.github.cinnaio.materiaengine;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

class StoredMachine {
    static final int SIZE = 27;

    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final ItemStack[] contents;
    private boolean running;
    private int elapsed;
    private String runningRecipeId;

    StoredMachine(UUID worldId, int x, int y, int z) {
        this(worldId, x, y, z, new ItemStack[SIZE], false, 0, null);
    }

    StoredMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.contents = normalize(contents);
        this.running = running;
        this.elapsed = Math.max(0, elapsed);
        this.runningRecipeId = runningRecipeId;
    }

    static String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    String key() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    UUID worldId() {
        return worldId;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    Location location(World world) {
        return new Location(world, x, y, z);
    }

    ItemStack[] contents() {
        return contents;
    }

    boolean running() {
        return running;
    }

    void running(boolean running) {
        this.running = running;
    }

    int elapsed() {
        return elapsed;
    }

    void elapsed(int elapsed) {
        this.elapsed = Math.max(0, elapsed);
    }

    String runningRecipeId() {
        return runningRecipeId;
    }

    void runningRecipeId(String runningRecipeId) {
        this.runningRecipeId = runningRecipeId;
    }

    static ItemStack[] normalize(ItemStack[] source) {
        ItemStack[] normalized = new ItemStack[SIZE];
        if (source == null) {
            return normalized;
        }
        System.arraycopy(source, 0, normalized, 0, Math.min(source.length, SIZE));
        return normalized;
    }
}
