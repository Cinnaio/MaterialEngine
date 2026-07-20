package com.github.cinnaio.materiaengine;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class MateriaEngineLang {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    MateriaEngineLang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        saveDefault("zh");
        saveDefault("us");
        languages.put("zh", load("zh"));
        languages.put("us", load("us"));
    }

    String text(CommandSender sender, String key) {
        return text(sender instanceof Player player ? player : null, key);
    }

    String text(Player player, String key) {
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
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + language + ".yml"));
    }

    private static String language(Player player) {
        if (player == null) {
            return "us";
        }
        Locale locale = player.locale();
        return locale != null && locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("zh") ? "zh" : "us";
    }
}
