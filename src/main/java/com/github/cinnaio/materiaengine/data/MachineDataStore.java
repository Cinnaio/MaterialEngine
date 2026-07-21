package com.github.cinnaio.materiaengine.data;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class MachineDataStore<T extends StoredMachine> {
    private final JavaPlugin plugin;
    private final File file;
    private final String table;
    private final String description;
    private final Function<Row, T> factory;

    public MachineDataStore(JavaPlugin plugin, String table, String description, Function<Row, T> factory) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.db");
        this.table = table;
        this.description = description;
        this.factory = factory;
        init();
    }

    public Map<String, T> load() {
        Map<String, T> machines = new LinkedHashMap<>();
        String sql = "SELECT * FROM " + table;
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                UUID worldId = UUID.fromString(rows.getString("world"));
                World world = Bukkit.getWorld(worldId);
                if (world == null) {
                    plugin.getLogger().warning("[MateriaEngine] Skip " + description + " in unloaded world: " + rows.getString("key"));
                    continue;
                }
                T machine = factory.apply(new Row(
                        worldId,
                        rows.getInt("x"),
                        rows.getInt("y"),
                        rows.getInt("z"),
                        deserializeItems(rows.getString("contents")),
                        rows.getInt("running") != 0,
                        rows.getInt("elapsed"),
                        rows.getString("running_recipe"),
                        rows.getInt("burn_time_left")
                ));
                machines.put(machine.key(), machine);
            }
        } catch (SQLException | IllegalArgumentException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to load " + description + " data: " + error.getMessage());
        }
        return machines;
    }

    public void save(Collection<T> machines) {
        String sql = """
                INSERT INTO %s(key, world, x, y, z, contents, running, elapsed, running_recipe, burn_time_left)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                  world = excluded.world,
                  x = excluded.x,
                  y = excluded.y,
                  z = excluded.z,
                  contents = excluded.contents,
                  running = excluded.running,
                  elapsed = excluded.elapsed,
                  running_recipe = excluded.running_recipe,
                  burn_time_left = excluded.burn_time_left
                """.formatted(table);
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T machine : machines) {
                statement.setString(1, machine.key());
                statement.setString(2, machine.worldId().toString());
                statement.setInt(3, machine.x());
                statement.setInt(4, machine.y());
                statement.setInt(5, machine.z());
                statement.setString(6, serializeItems(machine.contents()));
                statement.setInt(7, machine.running() ? 1 : 0);
                statement.setInt(8, machine.elapsed());
                statement.setString(9, machine.runningRecipeId());
                statement.setInt(10, machine.burnTimeLeft());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException | IOException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to save " + description + " data: " + error.getMessage());
        }
    }

    public void delete(String key) {
        String sql = "DELETE FROM " + table + " WHERE key = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to delete " + description + " data: " + error.getMessage());
        }
    }

    private void init() {
        plugin.getDataFolder().mkdirs();
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                  key TEXT PRIMARY KEY,
                  world TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  contents TEXT,
                  running INTEGER NOT NULL DEFAULT 0,
                  elapsed INTEGER NOT NULL DEFAULT 0,
                  running_recipe TEXT,
                  burn_time_left INTEGER NOT NULL DEFAULT 0
                )
                """.formatted(table);
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            addColumnIfMissing(statement, "burn_time_left INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to init " + description + " database: " + error.getMessage());
        }
    }

    private void addColumnIfMissing(Statement statement, String columnSql) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnSql);
        } catch (SQLException ignored) {
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    private static String serializeItems(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(items);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static ItemStack[] deserializeItems(String data) {
        if (data == null || data.isBlank()) {
            return new ItemStack[StoredMachine.SIZE];
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            Object value = input.readObject();
            return value instanceof ItemStack[] items ? StoredMachine.normalize(items) : new ItemStack[StoredMachine.SIZE];
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ignored) {
            return new ItemStack[StoredMachine.SIZE];
        }
    }

    public record Row(UUID worldId, int x, int y, int z, ItemStack[] contents, boolean running, int elapsed, String runningRecipeId, int burnTimeLeft) {
    }
}
