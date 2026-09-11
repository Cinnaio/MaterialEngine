package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.util.CraftEngineHook;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pending fruit harvests travel with their chunks, including across server restarts. */
public final class FruitRegrowth implements Listener {
    private final JavaPlugin plugin;
    private final CraftEngineHook hook;
    private final NamespacedKey rootKey;
    private final NamespacedKey blockIdKey;
    private final NamespacedKey dueKey;
    private final NamespacedKey positionKey;
    private final NamespacedKey legacyRootKey;
    private final NamespacedKey legacyBlockIdKey;
    private final NamespacedKey legacyDueKey;
    private final NamespacedKey legacyPositionKey;
    private final Map<ChunkKey, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public FruitRegrowth(JavaPlugin plugin, CraftEngineHook hook) {
        this.plugin = plugin;
        this.hook = hook;
        rootKey = new NamespacedKey(plugin, "fruit_regrowth");
        blockIdKey = new NamespacedKey(plugin, "block");
        dueKey = new NamespacedKey(plugin, "due");
        positionKey = new NamespacedKey(plugin, "position");
        legacyRootKey = new NamespacedKey("materiaengine", "fruit_regrowth");
        legacyBlockIdKey = new NamespacedKey("materiaengine", "block");
        legacyDueKey = new NamespacedKey("materiaengine", "due");
        legacyPositionKey = new NamespacedKey("materiaengine", "position");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) schedule(world, chunk.getX(), chunk.getZ());
        }
    }

    public void record(Block block, String id, long seconds) {
        if (!Boolean.FALSE.equals(hook.getBooleanState(block, "persistent"))) return;
        Chunk chunk = block.getChunk();
        var data = chunk.getPersistentDataContainer();
        migrateLegacy(data);
        var pending = data.getOrDefault(rootKey, PersistentDataType.TAG_CONTAINER,
                data.getAdapterContext().newPersistentDataContainer());
        var entry = data.getAdapterContext().newPersistentDataContainer();
        entry.set(blockIdKey, PersistentDataType.STRING, id);
        entry.set(dueKey, PersistentDataType.LONG, System.currentTimeMillis() + seconds * 1000);
        entry.set(positionKey, PersistentDataType.INTEGER_ARRAY, new int[]{block.getX(), block.getY(), block.getZ()});
        pending.set(new NamespacedKey(plugin, block.getX() + "_" + block.getY() + "_" + block.getZ()),
                PersistentDataType.TAG_CONTAINER, entry);
        data.set(rootKey, PersistentDataType.TAG_CONTAINER, pending);
        schedule(block.getWorld(), chunk.getX(), chunk.getZ());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        var data = event.getChunk().getPersistentDataContainer();
        if (data.has(rootKey) || data.has(legacyRootKey)) {
            migrateLegacy(data);
            schedule(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        ScheduledTask task = tasks.remove(new ChunkKey(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()));
        if (task != null) task.cancel();
    }

    public void shutdown() {
        closed = true;
        tasks.values().forEach(ScheduledTask::cancel);
        tasks.clear();
    }

    private void schedule(World world, int x, int z) {
        if (closed) return;
        ChunkKey key = new ChunkKey(world.getUID(), x, z);
        tasks.computeIfAbsent(key, ignored -> Bukkit.getRegionScheduler().runDelayed(plugin, world, x, z, task -> {
            tasks.remove(key, task);
            if (closed || !world.isChunkLoaded(x, z)) return;
            Chunk chunk = world.getChunkAt(x, z);
            if (process(chunk, System.currentTimeMillis())) schedule(world, x, z);
        }, 1200));
    }

    boolean process(Chunk chunk, long now) {
        var data = chunk.getPersistentDataContainer();
        migrateLegacy(data);
        var pending = data.get(rootKey, PersistentDataType.TAG_CONTAINER);
        if (pending == null) return false;
        if (!hook.isReady()) return true;
        for (NamespacedKey key : pending.getKeys()) {
            PersistentDataContainer entry = pending.get(key, PersistentDataType.TAG_CONTAINER);
            String id = entry == null ? null : entry.get(blockIdKey, PersistentDataType.STRING);
            int[] pos = entry == null ? null : entry.get(positionKey, PersistentDataType.INTEGER_ARRAY);
            Long due = entry == null ? null : entry.get(dueKey, PersistentDataType.LONG);
            if (id == null || pos == null || pos.length != 3 || due == null
                    || (pos[0] >> 4) != chunk.getX() || (pos[2] >> 4) != chunk.getZ()
                    || pos[1] < chunk.getWorld().getMinHeight() || pos[1] >= chunk.getWorld().getMaxHeight()) {
                pending.remove(key);
                continue;
            }
            Block block = chunk.getBlock(pos[0] & 15, pos[1], pos[2] & 15);
            if (!id.equals(hook.getBlockId(block)) || !Boolean.FALSE.equals(hook.getBooleanState(block, "persistent"))
                    || !Boolean.FALSE.equals(hook.getBooleanState(block, "fruiting"))) {
                pending.remove(key);
                continue;
            }
            if (due > now) continue;
            Integer distance = hook.getIntState(block, "distance");
            if (distance == null || distance >= 7) {
                pending.remove(key);
            } else if (hook.setBooleanState(block, id, "fruiting", true)) {
                pending.remove(key);
            }
        }
        if (pending.isEmpty()) data.remove(rootKey);
        else data.set(rootKey, PersistentDataType.TAG_CONTAINER, pending);
        return !pending.isEmpty();
    }

    private void migrateLegacy(PersistentDataContainer data) {
        if (legacyRootKey.equals(rootKey) || !data.has(legacyRootKey)) return;
        var legacyPending = data.get(legacyRootKey, PersistentDataType.TAG_CONTAINER);
        if (legacyPending == null) {
            data.remove(legacyRootKey);
            return;
        }
        var pending = data.getOrDefault(rootKey, PersistentDataType.TAG_CONTAINER,
                data.getAdapterContext().newPersistentDataContainer());
        for (NamespacedKey key : legacyPending.getKeys()) {
            var legacyEntry = legacyPending.get(key, PersistentDataType.TAG_CONTAINER);
            if (legacyEntry == null) continue;
            String id = first(legacyEntry, blockIdKey, legacyBlockIdKey, PersistentDataType.STRING);
            Long due = first(legacyEntry, dueKey, legacyDueKey, PersistentDataType.LONG);
            int[] position = first(legacyEntry, positionKey, legacyPositionKey, PersistentDataType.INTEGER_ARRAY);
            if (id == null || due == null || position == null) continue;
            try {
                NamespacedKey migratedKey = new NamespacedKey(plugin, key.getKey());
                if (pending.get(migratedKey, PersistentDataType.TAG_CONTAINER) != null) continue;
                var entry = data.getAdapterContext().newPersistentDataContainer();
                entry.set(blockIdKey, PersistentDataType.STRING, id);
                entry.set(dueKey, PersistentDataType.LONG, due);
                entry.set(positionKey, PersistentDataType.INTEGER_ARRAY, position);
                pending.set(migratedKey, PersistentDataType.TAG_CONTAINER, entry);
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy keys are discarded by the normal validation path.
            }
        }
        data.set(rootKey, PersistentDataType.TAG_CONTAINER, pending);
        data.remove(legacyRootKey);
    }

    private static <T> T first(PersistentDataContainer container, NamespacedKey current,
                               NamespacedKey legacy, PersistentDataType<T, T> type) {
        T value = container.get(current, type);
        return value == null ? container.get(legacy, type) : value;
    }

    private record ChunkKey(UUID world, int x, int z) { }
}
