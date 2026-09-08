package com.github.cinnaio.materiaengine.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

public record HarvestToolsConfig(String sickleItem, int radius, Map<String, Crop> crops,
                                 String basketItem, Map<String, Tool> tools,
                                 Map<String, String> fruits, long regrowSeconds) {
    public enum Mode { QUALITY, BONUS, FRUIT }

    public record Crop(String seed, int matureAge) { }

    public record Tool(String item, Mode mode, Map<String, String> targets, double chance,
                       Set<String> qualitySources, Map<String, Double> qualityTargets, double reach) {
        public String replacement(String original, RandomGenerator random) {
            if (mode != Mode.QUALITY || !qualitySources.contains(original) || random.nextDouble() >= chance) {
                return original;
            }
            double total = qualityTargets.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total <= 0) return original;
            double roll = random.nextDouble() * total;
            for (var target : qualityTargets.entrySet()) {
                roll -= target.getValue();
                if (roll < 0) return target.getKey();
            }
            return original;
        }
    }

    public static HarvestToolsConfig load(ConfigurationSection root) {
        ConfigurationSection defaults = root.getDefaultSection();
        if (defaults != null) {
            YamlConfiguration merged = new YamlConfiguration();
            copyValues(defaults, merged);
            copyValues(root, merged);
            root = merged;
        }
        Map<String, Crop> crops = new LinkedHashMap<>();
        ConfigurationSection cropSection = root.getConfigurationSection("sickle.crops");
        if (cropSection != null) {
            for (String id : cropSection.getKeys(false)) {
                ConfigurationSection spec = cropSection.getConfigurationSection(id);
                if (spec == null) continue;
                String seed = spec.getString("seed", "");
                if (!seed.isBlank()) crops.put(id, new Crop(seed, Math.max(1, spec.getInt("mature-age", 3))));
            }
        }
        Map<String, Tool> tools = new LinkedHashMap<>();
        ConfigurationSection toolSection = root.getConfigurationSection("harvest-tools.tools");
        if (root.getBoolean("harvest-tools.enabled", true) && toolSection != null) {
            for (String name : toolSection.getKeys(false)) {
                ConfigurationSection spec = toolSection.getConfigurationSection(name);
                if (spec == null || !spec.getBoolean("enabled", true)) continue;
                String item = spec.getString("item", "");
                if (item.isBlank()) continue;
                Mode mode = Mode.valueOf(spec.getString("mode", "bonus").toUpperCase(java.util.Locale.ROOT));
                Map<String, Double> weights = new LinkedHashMap<>();
                ConfigurationSection quality = spec.getConfigurationSection("quality-targets");
                if (quality != null) {
                    for (String id : quality.getKeys(false)) {
                        double weight = finite(quality.getDouble(id), 0);
                        if (weight > 0) weights.put(id, Math.min(weight, 10000));
                    }
                }
                Tool tool = new Tool(item, mode, strings(spec.getConfigurationSection("targets")),
                        Math.clamp(finite(spec.getDouble("chance", 0), 0), 0, 1),
                        Set.copyOf(spec.getStringList("quality-sources")), Collections.unmodifiableMap(weights),
                        Math.clamp(finite(spec.getDouble("reach", 5), 5), 1, 6));
                if (tools.putIfAbsent(item, tool) != null) throw new IllegalArgumentException("Duplicate harvest tool: " + item);
            }
        }
        return new HarvestToolsConfig(root.getString("sickle.item", "cgap:sickle"),
                Math.clamp(root.getInt("sickle.radius", 1), 0, 2), Map.copyOf(crops),
                root.getBoolean("harvest-tools.enabled", true) ? root.getString("harvest-tools.basket", "cgap:harvest_basket") : "",
                Map.copyOf(tools), strings(root.getConfigurationSection("harvest-tools.fruits")),
                Math.clamp(root.getLong("harvest-tools.regrow-seconds", 1200), 60, 604800));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static void copyValues(ConfigurationSection source, ConfigurationSection target) {
        source.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) target.set(path, value);
        });
    }

    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key, "");
                if (!value.isBlank()) values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }
}
