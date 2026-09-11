package com.github.cinnaio.teastory.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefinitionFilesTest {
    @TempDir
    Path directory;

    @Test
    void migratesLegacyConfigAndKeepsGlobalSettingsInConfig() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        doAnswer(ignored -> {
            if (!Files.exists(directory.resolve("config.yml"))) copyResource("config.yml", directory.resolve("config.yml"));
            return null;
        }).when(plugin).saveDefaultConfig();
        doAnswer(invocation -> {
            String resource = invocation.getArgument(0, String.class);
            Path target = directory.resolve(resource);
            if (!Files.exists(target)) copyResource(resource, target);
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));

        Files.writeString(directory.resolve("config.yml"), """
                machines:
                  demo-machine:
                    block:
                      id: cgap:demo_machine
                sickle:
                  item: cgap:old_sickle
                  crops:
                    cgap:demo_crop:
                      seed: cgap:demo_seed
                      mature-age: 4
                harvest-tools:
                  enabled: false
                  tools:
                    demo-tool:
                      item: cgap:demo_tool
                      mode: bonus
                  fruits:
                    cgap:demo_leaves: cgap:demo_fruit
                """);

        DefinitionFiles definitions = new DefinitionFiles(plugin);
        definitions.prepare();

        var migratedMachine = YamlConfiguration.loadConfiguration(directory.resolve("definitions/machines/demo-machine.yml").toFile());
        var migratedSickle = YamlConfiguration.loadConfiguration(directory.resolve("definitions/tools/sickle.yml").toFile());
        var migratedTool = YamlConfiguration.loadConfiguration(directory.resolve("definitions/tools/demo-tool.yml").toFile());
        var migratedFruitPicker = YamlConfiguration.loadConfiguration(directory.resolve("definitions/tools/fruit-picker.yml").toFile());
        var config = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());

        assertEquals("cgap:demo_machine", migratedMachine.getString("block.id"));
        assertEquals("cgap:old_sickle", migratedSickle.getString("item"));
        assertEquals("cgap:demo_seed", migratedSickle.getString("crops.cgap:demo_crop.seed"));
        assertEquals("cgap:demo_tool", migratedTool.getString("item"));
        assertEquals("cgap:demo_fruit", migratedFruitPicker.getString("fruits.cgap:demo_leaves"));
        assertFalse(config.isConfigurationSection("machines"));
        assertFalse(config.isConfigurationSection("sickle"));
        assertFalse(config.isConfigurationSection("harvest-tools.tools"));
        assertFalse(config.isConfigurationSection("harvest-tools.fruits"));
        assertFalse(config.getBoolean("harvest-tools.enabled", true));
        assertTrue(Files.isRegularFile(directory.resolve("definitions/machines/tea-table.yml")));
        assertTrue(Files.isRegularFile(directory.resolve("definitions/tools/tea-shears.yml")));
        assertEquals(122, definitions.loadMachine("tea-table").getConfigurationSection("recipes").getKeys(false).size());
    }

    private void copyResource(String resource, Path target) throws Exception {
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing test resource: " + resource);
            Files.copy(input, target);
        }
    }
}
