package com.github.cinnaio.materiaengine.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class StoredMachine {
    public static final int SIZE = 27;

    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final ItemStack[] contents;
    private boolean running;
    private int elapsed;
    private String runningRecipeId;

    public StoredMachine(UUID worldId, int x, int y, int z) {
        this(worldId, x, y, z, new ItemStack[SIZE], false, 0, null);
    }

    public StoredMachine(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.contents = normalize(contents);
        this.running = running;
        this.elapsed = Math.max(0, elapsed);
        this.runningRecipeId = runningRecipeId;
    }

    public static String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public String key() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    public UUID worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public Location location(World world) {
        return new Location(world, x, y, z);
    }

    public ItemStack[] contents() {
        return contents;
    }

    public boolean running() {
        return running;
    }

    public void running(boolean running) {
        this.running = running;
    }

    public int elapsed() {
        return elapsed;
    }

    public void elapsed(int elapsed) {
        this.elapsed = Math.max(0, elapsed);
    }

    public String runningRecipeId() {
        return runningRecipeId;
    }

    public void runningRecipeId(String runningRecipeId) {
        this.runningRecipeId = runningRecipeId;
    }

    public static ItemStack[] normalize(ItemStack[] source) {
        ItemStack[] normalized = new ItemStack[SIZE];
        if (source == null) {
            return normalized;
        }
        System.arraycopy(source, 0, normalized, 0, Math.min(source.length, SIZE));
        return normalized;
    }
}
