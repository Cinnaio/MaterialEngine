package com.github.cinnaio.teastory.i18n;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class TeaStoryLang {
    private final JavaPlugin plugin;
    private volatile Map<String, YamlConfiguration> languages = Map.of();

    public TeaStoryLang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        saveDefault("zh");
        saveDefault("us");
        languages = Map.of("zh", load("zh"), "us", load("us"));
    }

    public String text(CommandSender sender, String key) {
        return text(sender instanceof Player player ? player : null, key);
    }

    public String text(Player player, String key) {
        String language = language(player);
        String value = languages.getOrDefault(language, languages.get("us")).getString(key);
        if (value != null) {
            return value;
        }
        return languages.getOrDefault("us", new YamlConfiguration()).getString(key, key);
    }

    private void saveDefault(String language) {
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }
    }

    private YamlConfiguration load(String language) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + language + ".yml"));
        try (var resource = plugin.getResource("lang/" + language + ".yml")) {
            if (resource != null) configuration.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)));
        } catch (java.io.IOException error) {
            plugin.getLogger().warning("Failed to load default language " + language + ": " + error.getMessage());
        }
        return configuration;
    }

    private static String language(Player player) {
        if (player == null) {
            return "us";
        }
        Locale locale = player.locale();
        return locale != null && locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("zh") ? "zh" : "us";
    }
}
