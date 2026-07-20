package com.github.cinnaio.materiaengine;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TeaDryingPanDataStore {
    private final JavaPlugin plugin;
    private final File file;

    TeaDryingPanDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tea-drying-pans.yml");
    }

    Map<String, TeaDryingPanMachine> load() {
        Map<String, TeaDryingPanMachine> machines = new LinkedHashMap<>();
        if (!file.exists()) {
            return machines;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("machines");
        if (section == null) {
            return machines;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection machineSection = section.getConfigurationSection(key);
            if (machineSection == null) {
                continue;
            }
            try {
                UUID worldId = UUID.fromString(machineSection.getString("world", ""));
                World world = Bukkit.getWorld(worldId);
                if (world == null) {
                    plugin.getLogger().warning("[MateriaEngine] Skip tea drying pan in unloaded world: " + key);
                    continue;
                }
                TeaDryingPanMachine machine = new TeaDryingPanMachine(
                        worldId,
                        machineSection.getInt("x"),
                        machineSection.getInt("y"),
                        machineSection.getInt("z"),
                        loadItems(machineSection.getList("contents")),
                        machineSection.getBoolean("running"),
                        machineSection.getInt("elapsed"),
                        machineSection.getString("running-recipe")
                );
                machines.put(machine.key(), machine);
            } catch (IllegalArgumentException error) {
                plugin.getLogger().warning("[MateriaEngine] Skip broken tea drying pan data: " + key);
            }
        }
        return machines;
    }

    void save(Collection<TeaDryingPanMachine> machines) {
        YamlConfiguration config = new YamlConfiguration();
        for (TeaDryingPanMachine machine : machines) {
            String path = "machines." + machine.key();
            config.set(path + ".world", machine.worldId().toString());
            config.set(path + ".x", machine.x());
            config.set(path + ".y", machine.y());
            config.set(path + ".z", machine.z());
            config.set(path + ".running", machine.running());
            config.set(path + ".elapsed", machine.elapsed());
            config.set(path + ".running-recipe", machine.runningRecipeId());
            config.set(path + ".contents", Arrays.asList(machine.contents()));
        }
        try {
            config.save(file);
        } catch (IOException error) {
            plugin.getLogger().severe("[MateriaEngine] Failed to save tea drying pan data: " + error.getMessage());
        }
    }

    private static ItemStack[] loadItems(List<?> list) {
        ItemStack[] items = new ItemStack[TeaDryingPanMachine.SIZE];
        if (list == null) {
            return items;
        }
        for (int i = 0; i < Math.min(list.size(), items.length); i++) {
            Object item = list.get(i);
            items[i] = item instanceof ItemStack stack ? stack : null;
        }
        return items;
    }
}
