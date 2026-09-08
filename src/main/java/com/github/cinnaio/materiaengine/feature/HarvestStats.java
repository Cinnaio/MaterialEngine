package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Stats;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class HarvestStats {
    private final JavaPlugin plugin;
    private final File file;
    private final Object stateLock = new Object();
    private final Object ioLock = new Object();
    private final Map<Key, Summary> counters = new HashMap<>();
    private final Map<OutputKey, Output> outputs = new HashMap<>();
    private final Set<Key> dirtyCounters = new HashSet<>();
    private final Set<OutputKey> dirtyOutputs = new HashSet<>();
    private volatile Stats settings;
    private volatile boolean persistenceReady;
    private volatile boolean closed;
    private boolean started;
    private boolean failureLogged;
    private ScheduledTask flushTask;

    public HarvestStats(JavaPlugin plugin, Stats settings) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "harvest_stats.db");
        this.settings = settings;
        load();
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        schedule();
    }

    public synchronized void configure(Stats settings) {
        boolean intervalChanged = this.settings.flushSeconds() != settings.flushSeconds();
        this.settings = settings;
        if (started && intervalChanged) schedule();
    }

    private void schedule() {
        if (flushTask != null) flushTask.cancel();
        if (closed || !persistenceReady) return;
        flushTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> flush(),
                settings.flushSeconds(), settings.flushSeconds(), TimeUnit.SECONDS);
    }

    public void record(Player player, String tool, String crop, Map<String, Output> produced) {
        if (!settings.enabled() || player == null || tool == null || tool.isBlank() || crop == null || crop.isBlank()
                || (!settings.includeCreative() && player.getGameMode() == GameMode.CREATIVE)) return;
        Key key = new Key(player.getUniqueId(), tool, crop);
        synchronized (stateLock) {
            if (closed) return;
            Summary delta = Summary.empty();
            for (var entry : produced.entrySet()) {
                Output value = entry.getValue();
                if (value.items() <= 0) continue;
                OutputKey outputKey = new OutputKey(key, entry.getKey());
                outputs.merge(outputKey, value, Output::add);
                dirtyOutputs.add(outputKey);
                delta = delta.add(new Summary(0, value.items(), value.collected(), value.dropped(), value.quality(), value.bonus()));
            }
            delta = delta.add(new Summary(1, 0, 0, 0, 0, 0));
            counters.merge(key, delta, Summary::add);
            dirtyCounters.add(key);
        }
    }

    public boolean enabled() { return settings.enabled(); }

    public boolean persistenceReady() { return persistenceReady; }

    public Summary summary(UUID player) {
        synchronized (stateLock) {
            return counters.entrySet().stream().filter(entry -> player == null || entry.getKey().player().equals(player))
                    .map(Map.Entry::getValue).reduce(Summary.empty(), Summary::add);
        }
    }

    public List<Breakdown> breakdown(UUID player, int limit) {
        Map<Group, Summary> groups = new HashMap<>();
        synchronized (stateLock) {
            counters.forEach((key, value) -> {
                if (player == null || key.player().equals(player)) {
                    groups.merge(new Group(key.tool(), key.crop()), value, Summary::add);
                }
            });
        }
        return groups.entrySet().stream().map(entry -> new Breakdown(entry.getKey().tool(), entry.getKey().crop(), entry.getValue()))
                .sorted(Comparator.comparingLong((Breakdown row) -> row.totals().harvests()).reversed()
                        .thenComparing(Breakdown::tool).thenComparing(Breakdown::crop))
                .limit(Math.max(1, limit)).toList();
    }

    public List<ItemTotal> items(UUID player, int limit) {
        Map<String, Output> groups = new HashMap<>();
        synchronized (stateLock) {
            outputs.forEach((key, value) -> {
                if (player == null || key.harvest().player().equals(player)) groups.merge(key.item(), value, Output::add);
            });
        }
        return groups.entrySet().stream().map(entry -> new ItemTotal(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong((ItemTotal row) -> row.output().items()).reversed().thenComparing(ItemTotal::item))
                .limit(Math.max(1, limit)).toList();
    }

    public synchronized void shutdown() {
        if (flushTask != null) flushTask.cancel();
        synchronized (stateLock) { closed = true; }
        flush();
    }

    // Snapshot only dirty rows under the short state lock; JDBC never holds up a harvest.
    boolean flush() {
        synchronized (ioLock) {
            if (!persistenceReady) return false;
            Map<Key, Summary> changed = new HashMap<>();
            Map<OutputKey, Output> changedOutputs = new HashMap<>();
            synchronized (stateLock) {
                dirtyCounters.forEach(key -> changed.put(key, counters.get(key)));
                dirtyOutputs.forEach(key -> changedOutputs.put(key, outputs.get(key)));
                dirtyCounters.clear();
                dirtyOutputs.clear();
            }
            if (changed.isEmpty() && changedOutputs.isEmpty()) return true;
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                try (PreparedStatement totals = connection.prepareStatement("""
                        INSERT INTO harvest_stats VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(player_uuid, tool_id, crop_id) DO UPDATE SET
                          harvests=excluded.harvests, items=excluded.items, collected=excluded.collected,
                          dropped=excluded.dropped, quality_items=excluded.quality_items, bonus_items=excluded.bonus_items
                        """);
                     PreparedStatement items = connection.prepareStatement("""
                        INSERT INTO harvest_outputs VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(player_uuid, tool_id, crop_id, item_id) DO UPDATE SET
                          collected=excluded.collected, dropped=excluded.dropped,
                          quality_items=excluded.quality_items, bonus_items=excluded.bonus_items
                        """)) {
                    for (var entry : changed.entrySet()) {
                        bindKey(totals, entry.getKey());
                        Summary value = entry.getValue();
                        totals.setLong(4, value.harvests());
                        totals.setLong(5, value.items());
                        totals.setLong(6, value.collectedItems());
                        totals.setLong(7, value.droppedItems());
                        totals.setLong(8, value.qualityItems());
                        totals.setLong(9, value.bonusItems());
                        totals.addBatch();
                    }
                    for (var entry : changedOutputs.entrySet()) {
                        bindKey(items, entry.getKey().harvest());
                        items.setString(4, entry.getKey().item());
                        Output value = entry.getValue();
                        items.setLong(5, value.collected());
                        items.setLong(6, value.dropped());
                        items.setLong(7, value.quality());
                        items.setLong(8, value.bonus());
                        items.addBatch();
                    }
                    totals.executeBatch();
                    items.executeBatch();
                    connection.commit();
                } catch (SQLException error) {
                    connection.rollback();
                    throw error;
                }
                failureLogged = false;
                return true;
            } catch (SQLException error) {
                synchronized (stateLock) {
                    dirtyCounters.addAll(changed.keySet());
                    dirtyOutputs.addAll(changedOutputs.keySet());
                }
                if (!failureLogged || closed) {
                    plugin.getLogger().warning("[MateriaEngine] Harvest statistics remain pending after save failure: " + error.getMessage());
                    failureLogged = true;
                }
                return false;
            }
        }
    }

    private void load() {
        plugin.getDataFolder().mkdirs();
        Map<Key, Summary> loaded = new HashMap<>();
        Map<OutputKey, Output> loadedOutputs = new HashMap<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS harvest_stats (
                      player_uuid TEXT NOT NULL, tool_id TEXT NOT NULL, crop_id TEXT NOT NULL,
                      harvests INTEGER NOT NULL, items INTEGER NOT NULL,
                      collected INTEGER NOT NULL, dropped INTEGER NOT NULL,
                      quality_items INTEGER NOT NULL, bonus_items INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, tool_id, crop_id))
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS harvest_outputs (
                      player_uuid TEXT NOT NULL, tool_id TEXT NOT NULL, crop_id TEXT NOT NULL, item_id TEXT NOT NULL,
                      collected INTEGER NOT NULL, dropped INTEGER NOT NULL,
                      quality_items INTEGER NOT NULL, bonus_items INTEGER NOT NULL,
                      PRIMARY KEY(player_uuid, tool_id, crop_id, item_id))
                    """);
            try (ResultSet rows = statement.executeQuery("SELECT * FROM harvest_stats")) {
                while (rows.next()) loaded.put(readKey(rows), new Summary(rows.getLong("harvests"), rows.getLong("items"),
                        rows.getLong("collected"), rows.getLong("dropped"), rows.getLong("quality_items"), rows.getLong("bonus_items")));
            }
            try (ResultSet rows = statement.executeQuery("SELECT * FROM harvest_outputs")) {
                while (rows.next()) loadedOutputs.put(new OutputKey(readKey(rows), rows.getString("item_id")),
                        new Output(rows.getLong("collected"), rows.getLong("dropped"), rows.getLong("quality_items"), rows.getLong("bonus_items")));
            }
            counters.putAll(loaded);
            outputs.putAll(loadedOutputs);
            persistenceReady = true;
        } catch (SQLException | IllegalArgumentException error) {
            plugin.getLogger().severe("[MateriaEngine] Harvest statistics load failed; existing database will not be overwritten: " + error.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    private static Key readKey(ResultSet rows) throws SQLException {
        return new Key(UUID.fromString(rows.getString("player_uuid")), rows.getString("tool_id"), rows.getString("crop_id"));
    }

    private static void bindKey(PreparedStatement statement, Key key) throws SQLException {
        statement.setString(1, key.player().toString());
        statement.setString(2, key.tool());
        statement.setString(3, key.crop());
    }

    public record Summary(long harvests, long items, long collectedItems, long droppedItems, long qualityItems, long bonusItems) {
        static Summary empty() { return new Summary(0, 0, 0, 0, 0, 0); }

        Summary add(Summary value) {
            return new Summary(harvests + value.harvests, items + value.items, collectedItems + value.collectedItems,
                    droppedItems + value.droppedItems, qualityItems + value.qualityItems, bonusItems + value.bonusItems);
        }
    }

    public record Output(long collected, long dropped, long quality, long bonus) {
        public long items() { return collected + dropped; }

        Output add(Output value) {
            return new Output(collected + value.collected, dropped + value.dropped, quality + value.quality, bonus + value.bonus);
        }
    }

    public record Breakdown(String tool, String crop, Summary totals) { }
    public record ItemTotal(String item, Output output) { }
    private record Key(UUID player, String tool, String crop) { }
    private record OutputKey(Key harvest, String item) { }
    private record Group(String tool, String crop) { }
}
