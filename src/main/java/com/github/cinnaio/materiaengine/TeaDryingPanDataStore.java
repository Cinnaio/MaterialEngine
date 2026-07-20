package com.github.cinnaio.materiaengine;

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

final class TeaDryingPanDataStore {
    private final JavaPlugin plugin;
    private final File file;

    TeaDryingPanDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.db");
        init();
    }

    Map<String, TeaDryingPanMachine> load() {
        Map<String, TeaDryingPanMachine> machines = new LinkedHashMap<>();
        String sql = "SELECT * FROM tea_drying_pans";
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                UUID worldId = UUID.fromString(rows.getString("world"));
                World world = Bukkit.getWorld(worldId);
                if (world == null) {
                    plugin.getLogger().warning("[MateriaEngine] Skip tea drying pan in unloaded world: " + rows.getString("key"));
                    continue;
                }
                TeaDryingPanMachine machine = new TeaDryingPanMachine(
                        worldId,
                        rows.getInt("x"),
                        rows.getInt("y"),
                        rows.getInt("z"),
                        deserializeItems(rows.getString("contents")),
                        rows.getInt("running") != 0,
                        rows.getInt("elapsed"),
                        rows.getString("running_recipe")
                );
                machines.put(machine.key(), machine);
            }
        } catch (SQLException | IllegalArgumentException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to load tea drying pan data: " + error.getMessage());
        }
        return machines;
    }

    void save(Collection<TeaDryingPanMachine> machines) {
        String sql = """
                INSERT INTO tea_drying_pans(key, world, x, y, z, contents, running, elapsed, running_recipe)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                  world = excluded.world,
                  x = excluded.x,
                  y = excluded.y,
                  z = excluded.z,
                  contents = excluded.contents,
                  running = excluded.running,
                  elapsed = excluded.elapsed,
                  running_recipe = excluded.running_recipe
                """;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TeaDryingPanMachine machine : machines) {
                statement.setString(1, machine.key());
                statement.setString(2, machine.worldId().toString());
                statement.setInt(3, machine.x());
                statement.setInt(4, machine.y());
                statement.setInt(5, machine.z());
                statement.setString(6, serializeItems(machine.contents()));
                statement.setInt(7, machine.running() ? 1 : 0);
                statement.setInt(8, machine.elapsed());
                statement.setString(9, machine.runningRecipeId());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException | IOException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to save tea drying pan data: " + error.getMessage());
        }
    }

    private void init() {
        plugin.getDataFolder().mkdirs();
        String sql = """
                CREATE TABLE IF NOT EXISTS tea_drying_pans (
                  key TEXT PRIMARY KEY,
                  world TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  contents TEXT,
                  running INTEGER NOT NULL DEFAULT 0,
                  elapsed INTEGER NOT NULL DEFAULT 0,
                  running_recipe TEXT
                )
                """;
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to init tea drying pan database: " + error.getMessage());
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
            return new ItemStack[TeaDryingPanMachine.SIZE];
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            Object value = input.readObject();
            return value instanceof ItemStack[] items ? normalize(items) : new ItemStack[TeaDryingPanMachine.SIZE];
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ignored) {
            return new ItemStack[TeaDryingPanMachine.SIZE];
        }
    }

    private static ItemStack[] normalize(ItemStack[] source) {
        ItemStack[] normalized = new ItemStack[TeaDryingPanMachine.SIZE];
        System.arraycopy(source, 0, normalized, 0, Math.min(source.length, normalized.length));
        return normalized;
    }
}
