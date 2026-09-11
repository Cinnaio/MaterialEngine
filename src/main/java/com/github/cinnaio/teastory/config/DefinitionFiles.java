package com.github.cinnaio.teastory.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Owns the user-editable machine and harvest-tool definition files.
 *
 * <p>The main config intentionally contains only runtime settings. Definitions
 * live below {@code definitions/machines} and {@code definitions/tools} so a
 * single machine or tool can be changed without navigating a large YAML file.</p>
 */
public final class DefinitionFiles {
    private static final String ROOT = "definitions";
    private static final String MACHINES = "machines";
    private static final String TOOLS = "tools";
    private static final List<String> DEFAULT_MACHINE_FILES = List.of(
            "tea-drying-pan.yml",
            "teapan.yml",
            "barrel.yml",
            "tea-stove.yml",
            "tea-table.yml"
    );
    private static final List<String> DEFAULT_TOOL_FILES = List.of(
            "sickle.yml",
            "tea-shears.yml",
            "herb-shears.yml",
            "root-spade.yml",
            "fruit-picker.yml"
    );

    private final JavaPlugin plugin;
    private final Path dataFolder;
    private final Path definitionsFolder;

    public DefinitionFiles(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.definitionsFolder = dataFolder.resolve(ROOT);
    }

    /**
     * Creates the new layout and migrates the old plugin layout without
     * deleting anything from the old data folder.
     */
    public void prepare() {
        migrateLegacyDataFolder();
        plugin.saveDefaultConfig();
        migrateLegacyConfiguration();
        saveDefaults(MACHINES, DEFAULT_MACHINE_FILES);
        saveDefaults(TOOLS, DEFAULT_TOOL_FILES);
    }

    public YamlConfiguration loadMachine(String id) {
        return loadDefinition(MACHINES, id);
    }

    public Map<String, YamlConfiguration> loadTools() {
        return loadDefinitions(TOOLS);
    }

    /**
     * Loads bundled tool definitions for callers that do not have a plugin
     * data directory, notably configuration unit tests and old integrations.
     */
    public static Map<String, YamlConfiguration> loadBundledTools() {
        Map<String, YamlConfiguration> definitions = new LinkedHashMap<>();
        for (String file : DEFAULT_TOOL_FILES) {
            String id = idOf(file);
            YamlConfiguration configuration = loadResource(ROOT + "/" + TOOLS + "/" + file);
            if (configuration != null) {
                definitions.put(id, configuration);
            }
        }
        return definitions;
    }

    private Map<String, YamlConfiguration> loadDefinitions(String category) {
        Path folder = definitionsFolder.resolve(category);
        if (!Files.isDirectory(folder)) {
            return Map.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream.filter(DefinitionFiles::isYaml)
                    .sorted()
                    .toList();
        } catch (IOException error) {
            plugin.getLogger().warning("[TeaStory] Failed to list " + category + " definitions: " + error.getMessage());
            return Map.of();
        }

        Map<String, YamlConfiguration> definitions = new LinkedHashMap<>();
        for (Path file : files) {
            String id = idOf(file.getFileName().toString());
            YamlConfiguration configuration = loadDefinitionFile(file,
                    ROOT + "/" + category + "/" + file.getFileName());
            definitions.put(id, configuration);
        }
        return definitions;
    }

    private YamlConfiguration loadDefinition(String category, String id) {
        String fileName = safeFileName(id);
        Path file = definitionsFolder.resolve(category).resolve(fileName + ".yml").normalize();
        Path categoryFolder = definitionsFolder.resolve(category).normalize();
        if (!file.startsWith(categoryFolder) || !Files.isRegularFile(file)) {
            return new YamlConfiguration();
        }
        return loadDefinitionFile(file, ROOT + "/" + category + "/" + fileName + ".yml");
    }

    private YamlConfiguration loadDefinitionFile(Path file, String resourcePath) {
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file.toFile());
        YamlConfiguration defaults = loadResource(resourcePath);
        if (defaults == null) {
            return user;
        }
        YamlConfiguration merged = new YamlConfiguration();
        copyValues(defaults, merged);
        copyValues(user, merged);
        return merged;
    }

    private void saveDefaults(String category, List<String> files) {
        Path folder = definitionsFolder.resolve(category);
        try {
            Files.createDirectories(folder);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create " + folder, error);
        }
        for (String file : files) {
            Path target = folder.resolve(file);
            if (Files.exists(target)) {
                continue;
            }
            try {
                plugin.saveResource(ROOT + "/" + category + "/" + file, false);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Missing bundled definition " + ROOT + "/" + category + "/" + file, error);
            }
        }
    }

    private void migrateLegacyDataFolder() {
        Path parent = dataFolder.getParent();
        if (parent == null) {
            return;
        }
        Path legacy = parent.resolve("MateriaEngine").normalize();
        Path current = dataFolder.normalize();
        if (!Files.isDirectory(legacy) || legacy.equals(current)) {
            return;
        }
        int copied = 0;
        try (Stream<Path> stream = Files.walk(legacy)) {
            for (Path source : stream.sorted().toList()) {
                Path relative = legacy.relativize(source);
                Path target = current.resolve(relative).normalize();
                if (!target.startsWith(current)) {
                    continue;
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source) && !Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                    copied++;
                }
            }
        } catch (IOException error) {
            plugin.getLogger().warning("[TeaStory] Failed to migrate legacy data: " + error.getMessage());
            return;
        }
        if (copied > 0) {
            plugin.getLogger().info("[TeaStory] Migrated " + copied + " file(s) from plugins/MateriaEngine.");
        }
    }

    private void migrateLegacyConfiguration() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        changed |= migrateMachineSection(legacy);
        changed |= migrateToolSection(legacy, "sickle", "sickle");
        changed |= migrateToolSection(legacy, "harvest-tools.tools", null);
        changed |= migrateFruitSection(legacy);
        if (!changed) {
            return;
        }
        legacy.set("machines", null);
        legacy.set("sickle", null);
        legacy.set("harvest-tools.tools", null);
        legacy.set("harvest-tools.fruits", null);
        try {
            legacy.save(file);
            plugin.getLogger().info("[TeaStory] Migrated machine and harvest-tool definitions into definitions/.");
        } catch (IOException error) {
            throw new IllegalStateException("Unable to save migrated config.yml", error);
        }
    }

    private boolean migrateMachineSection(YamlConfiguration legacy) {
        ConfigurationSection machines = legacy.getConfigurationSection("machines");
        if (machines == null) {
            return false;
        }
        boolean found = false;
        for (String id : machines.getKeys(false)) {
            ConfigurationSection definition = machines.getConfigurationSection(id);
            if (definition != null) {
                found |= migrateDefinition(MACHINES, id, definition);
            }
        }
        return found;
    }

    private boolean migrateToolSection(YamlConfiguration legacy, String path, String fixedId) {
        ConfigurationSection section = legacy.getConfigurationSection(path);
        if (section == null) {
            return false;
        }
        if (fixedId != null) {
            return migrateDefinition(TOOLS, fixedId, section);
        }
        boolean found = false;
        for (String id : section.getKeys(false)) {
            ConfigurationSection definition = section.getConfigurationSection(id);
            if (definition != null) {
                found |= migrateDefinition(TOOLS, id, definition);
            }
        }
        return found;
    }

    private boolean migrateFruitSection(YamlConfiguration legacy) {
        ConfigurationSection fruits = legacy.getConfigurationSection("harvest-tools.fruits");
        if (fruits == null) {
            return false;
        }
        Path target = definitionsFolder.resolve(TOOLS).resolve("fruit-picker.yml");
        try {
            Files.createDirectories(target.getParent());
            YamlConfiguration output = Files.isRegularFile(target)
                    ? YamlConfiguration.loadConfiguration(target.toFile())
                    : loadResource(ROOT + "/" + TOOLS + "/fruit-picker.yml");
            if (output == null) {
                output = new YamlConfiguration();
            }
            copySection(fruits, output, "fruits");
            output.save(target.toFile());
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to migrate harvest-tool fruit mappings", error);
        }
    }

    private boolean migrateDefinition(String category, String id, ConfigurationSection definition) {
        String fileName = safeFileName(id);
        if (fileName.isBlank()) {
            plugin.getLogger().warning("[TeaStory] Skipped invalid legacy definition id: " + id);
            return false;
        }
        Path folder = definitionsFolder.resolve(category);
        Path target = folder.resolve(fileName + ".yml");
        if (Files.exists(target)) {
            return false;
        }
        try {
            Files.createDirectories(folder);
            YamlConfiguration output = new YamlConfiguration();
            copySection(definition, output, "");
            output.save(target.toFile());
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to migrate " + category + "/" + fileName + ".yml", error);
        }
    }

    private static YamlConfiguration loadResource(String resourcePath) {
        try (InputStream input = DefinitionFiles.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException error) {
            return null;
        }
    }

    private static void copyValues(ConfigurationSection source, ConfigurationSection target) {
        source.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) {
                target.set(path, value);
            }
        });
    }

    private static void copySection(ConfigurationSection source, ConfigurationSection target, String prefix) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            String path = prefix.isBlank() ? key : prefix + "." + key;
            if (value instanceof ConfigurationSection section) {
                copySection(section, target, path);
            } else {
                target.set(path, value);
            }
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return Files.isRegularFile(path) && name.endsWith(".yml");
    }

    private static String idOf(String fileName) {
        return fileName.substring(0, fileName.length() - 4);
    }

    private static String safeFileName(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            return "";
        }
        return id;
    }
}
