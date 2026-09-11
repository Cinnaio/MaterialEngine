package com.github.cinnaio.teastory.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

public record HarvestToolsConfig(String sickleItem, int radius, Map<String, Crop> crops,
                                 String basketItem, Map<String, Tool> tools,
                                 Map<String, String> fruits, long regrowSeconds,
                                 int sickleCooldownTicks, int sickleDurabilityCost,
                                 Feedback feedback, Stats stats, Map<String, Long> regrowOverrides, Pouch pouch) {
    public enum Mode { QUALITY, BONUS, FRUIT }

    public record Crop(String seed, int matureAge) { }

    public record Pouch(boolean enabled, String item, int slots) {
        static Pouch load(ConfigurationSection root) {
            int slots = Math.clamp(root.getInt("harvest-tools.seed-pouch.slots", 9), 9, 27);
            return new Pouch(root.getBoolean("harvest-tools.seed-pouch.enabled", true),
                    root.getString("harvest-tools.seed-pouch.item", "cgap:seed_pouch"), ((slots + 8) / 9) * 9);
        }
    }

    public long regrowSeconds(String leaf) {
        return regrowOverrides.getOrDefault(leaf, regrowSeconds);
    }

    public record Tool(String item, Mode mode, Map<String, String> targets, double chance,
                       Set<String> qualitySources, Map<String, Double> qualityTargets, double reach,
                       int cooldownTicks, int durabilityCost, int bonusAmount, Map<String, Double> targetChances) {
        public double chanceFor(String crop) {
            return targetChances.getOrDefault(crop, chance);
        }

        public String replacement(String original, RandomGenerator random) {
            return replacement(original, chance, random);
        }

        public String replacement(String original, String crop, RandomGenerator random) {
            return replacement(original, chanceFor(crop), random);
        }

        private String replacement(String original, double probability, RandomGenerator random) {
            if (mode != Mode.QUALITY || !qualitySources.contains(original) || random.nextDouble() >= probability) {
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

    public record Feedback(boolean enabled, boolean actionbar, boolean sounds, boolean particles,
                           Effect success, Effect bonus, Effect quality, Effect fruit,
                           Effect failure, Effect cooldown, int failureIntervalTicks) {
        static Feedback load(ConfigurationSection root) {
            ConfigurationSection section = root.getConfigurationSection("harvest-tools.feedback");
            if (section == null) {
                return new Feedback(true, true, true, true,
                        Effect.defaults("minecraft:block.grass.break", 0.65f, 1.1f, "HAPPY_VILLAGER", 4, 0.25),
                        Effect.defaults("minecraft:entity.player.levelup", 0.45f, 1.8f, "COMPOSTER", 6, 0.3),
                        Effect.defaults("minecraft:block.amethyst_block.chime", 0.55f, 1.5f, "ENCHANT", 8, 0.25),
                        Effect.defaults("minecraft:block.azalea_leaves.break", 0.65f, 1.0f, "HAPPY_VILLAGER", 5, 0.25),
                        Effect.defaults("minecraft:block.note_block.bass", 0.35f, 0.7f, "SMOKE", 0, 0),
                        Effect.defaults("minecraft:block.note_block.bass", 0.25f, 0.5f, "", 0, 0), 20);
            }
            return new Feedback(section.getBoolean("enabled", true), section.getBoolean("actionbar", true),
                    section.getBoolean("sounds", true), section.getBoolean("particles", true),
                    Effect.load(section.getConfigurationSection("success"),
                            "minecraft:block.grass.break", 0.65f, 1.1f, "HAPPY_VILLAGER", 4, 0.25),
                    Effect.load(section.getConfigurationSection("bonus"),
                            "minecraft:entity.player.levelup", 0.45f, 1.8f, "COMPOSTER", 6, 0.3),
                    Effect.load(section.getConfigurationSection("quality"),
                            "minecraft:block.amethyst_block.chime", 0.55f, 1.5f, "ENCHANT", 8, 0.25),
                    Effect.load(section.getConfigurationSection("fruit"),
                            "minecraft:block.azalea_leaves.break", 0.65f, 1.0f, "HAPPY_VILLAGER", 5, 0.25),
                    Effect.load(section.getConfigurationSection("failure"),
                            "minecraft:block.note_block.bass", 0.35f, 0.7f, "SMOKE", 0, 0),
                    Effect.load(section.getConfigurationSection("cooldown"),
                            "minecraft:block.note_block.bass", 0.25f, 0.5f, "", 0, 0),
                    Math.clamp(section.getInt("failure-interval-ticks", 20), 0, 200));
        }
    }

    public record Effect(String sound, float volume, float pitch, String particle, int count, double offset) {
        private static Effect load(ConfigurationSection section, String sound, float volume, float pitch,
                                   String particle, int count, double offset) {
            if (section == null) return defaults(sound, volume, pitch, particle, count, offset);
            return defaults(section.getString("sound", sound),
                    (float) clampFinite(section.getDouble("volume", volume), volume, 0, 2),
                    (float) clampFinite(section.getDouble("pitch", pitch), pitch, 0, 2),
                    section.getString("particle", particle),
                    Math.clamp(section.getInt("count", count), 0, 64),
                    clampFinite(section.getDouble("offset", offset), offset, 0, 2));
        }

        private static Effect defaults(String sound, float volume, float pitch, String particle, int count, double offset) {
            if (particle != null && !particle.isBlank()) {
                Particle parsed = Particle.valueOf(particle.trim().toUpperCase(java.util.Locale.ROOT));
                if (parsed.getDataType() != Void.class) {
                    throw new IllegalArgumentException("Harvest particle requires additional data: " + particle);
                }
                particle = parsed.name();
            }
            return new Effect(sound == null ? "" : sound.trim(), volume, pitch,
                    particle == null ? "" : particle.trim(), count, offset);
        }

        private static double clampFinite(double value, double fallback, double min, double max) {
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        }
    }

    public record Stats(boolean enabled, boolean includeCreative, int flushSeconds, java.time.ZoneId timezone) {
        public Stats(boolean enabled, boolean includeCreative, int flushSeconds) {
            this(enabled, includeCreative, flushSeconds, java.time.ZoneId.of("Asia/Shanghai"));
        }

        static Stats load(ConfigurationSection root) {
            ConfigurationSection section = root.getConfigurationSection("harvest-tools.stats");
            if (section == null) return new Stats(true, false, 30);
            return new Stats(section.getBoolean("enabled", true), section.getBoolean("include-creative", false),
                    Math.clamp(section.getInt("flush-seconds", 30), 5, 3600),
                    timezone(section.getString("timezone", "Asia/Shanghai")));
        }

        private static java.time.ZoneId timezone(String name) {
            try { return java.time.ZoneId.of(name); }
            catch (java.time.DateTimeException error) { throw new IllegalArgumentException("Invalid harvest statistics timezone: " + name, error); }
        }
    }

    public static HarvestToolsConfig load(ConfigurationSection root) {
        YamlConfiguration merged = mergeDefaults(root);
        Map<String, YamlConfiguration> definitions = new LinkedHashMap<>(DefinitionFiles.loadBundledTools());
        overlayLegacyDefinitions(merged, definitions);
        return load(merged, definitions);
    }

    public static HarvestToolsConfig load(ConfigurationSection root,
                                          Map<String, ? extends ConfigurationSection> definitions) {
        root = mergeDefaults(root);
        return loadDefinitions(root, definitions);
    }

    private static HarvestToolsConfig loadDefinitions(ConfigurationSection root,
                                                      Map<String, ? extends ConfigurationSection> definitions) {
        ConfigurationSection sickle = definitions.get("sickle");
        if (sickle == null) {
            sickle = root.getConfigurationSection("sickle");
        }
        Map<String, Crop> crops = new LinkedHashMap<>();
        ConfigurationSection cropSection = sickle == null ? null : sickle.getConfigurationSection("crops");
        if (cropSection != null) {
            for (String id : cropSection.getKeys(false)) {
                ConfigurationSection spec = cropSection.getConfigurationSection(id);
                if (spec == null) continue;
                String seed = spec.getString("seed", "");
                if (!seed.isBlank()) crops.put(id, new Crop(seed, Math.max(1, spec.getInt("mature-age", 3))));
            }
        }
        int defaultCooldownTicks = clampInt(root.getInt("harvest-tools.cooldown-ticks", 4), 0, 200);
        int defaultDurabilityCost = clampInt(root.getInt("harvest-tools.durability-cost", 1), 0, 100);
        Map<String, Tool> tools = new LinkedHashMap<>();
        if (root.getBoolean("harvest-tools.enabled", true)) {
            for (Map.Entry<String, ? extends ConfigurationSection> entry : definitions.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("sickle")) continue;
                ConfigurationSection spec = entry.getValue();
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
                Map<String, Double> targetChances = new LinkedHashMap<>();
                ConfigurationSection chances = spec.getConfigurationSection("target-chances");
                if (chances != null) {
                    for (String id : chances.getKeys(false)) {
                        targetChances.put(id, Math.clamp(finite(chances.getDouble(id), 0), 0, 1));
                    }
                }
                Tool tool = new Tool(item, mode, strings(spec.getConfigurationSection("targets")),
                        Math.clamp(finite(spec.getDouble("chance", 0), 0), 0, 1),
                        Set.copyOf(spec.getStringList("quality-sources")), Collections.unmodifiableMap(weights),
                        Math.clamp(finite(spec.getDouble("reach", 5), 5), 1, 6),
                        clampInt(spec.getInt("cooldown-ticks", defaultCooldownTicks), 0, 200),
                        clampInt(spec.getInt("durability-cost", defaultDurabilityCost), 0, 100),
                        clampInt(spec.getInt("bonus-amount", 1), 0, 64), Map.copyOf(targetChances));
                if (tools.putIfAbsent(item, tool) != null) throw new IllegalArgumentException("Duplicate harvest tool: " + item);
            }
        }
        Map<String, Long> regrowOverrides = new LinkedHashMap<>();
        ConfigurationSection regrow = root.getConfigurationSection("harvest-tools.regrow-overrides");
        if (regrow != null) {
            for (String id : regrow.getKeys(false)) regrowOverrides.put(id, Math.clamp(regrow.getLong(id), 60L, 604800L));
        }
        Map<String, String> fruits = strings(root.getConfigurationSection("harvest-tools.fruits"));
        ConfigurationSection fruitPicker = definitions.get("fruit-picker");
        if (fruitPicker != null && fruitPicker.getConfigurationSection("fruits") != null) {
            fruits = strings(fruitPicker.getConfigurationSection("fruits"));
        }
        String sickleItem = sickle == null ? root.getString("sickle.item", "cgap:sickle")
                : sickle.getString("item", root.getString("sickle.item", "cgap:sickle"));
        int sickleRadius = sickle == null ? root.getInt("sickle.radius", 1) : sickle.getInt("radius", root.getInt("sickle.radius", 1));
        int sickleCooldown = sickle == null ? root.getInt("sickle.cooldown-ticks", defaultCooldownTicks)
                : sickle.getInt("cooldown-ticks", root.getInt("sickle.cooldown-ticks", defaultCooldownTicks));
        int sickleDurability = sickle == null ? root.getInt("sickle.durability-cost", defaultDurabilityCost)
                : sickle.getInt("durability-cost", root.getInt("sickle.durability-cost", defaultDurabilityCost));
        HarvestToolsConfig result = new HarvestToolsConfig(sickleItem,
                Math.clamp(sickleRadius, 0, 2), Map.copyOf(crops),
                root.getBoolean("harvest-tools.enabled", true) ? root.getString("harvest-tools.basket", "cgap:harvest_basket") : "",
                Map.copyOf(tools), fruits,
                Math.clamp(root.getLong("harvest-tools.regrow-seconds", 1200), 60, 604800),
                clampInt(sickleCooldown, 0, 200),
                clampInt(sickleDurability, 0, 100),
                Feedback.load(root), Stats.load(root), Map.copyOf(regrowOverrides), Pouch.load(root));
        return result;
    }

    private static YamlConfiguration mergeDefaults(ConfigurationSection root) {
        ConfigurationSection defaults = root.getDefaultSection();
        if (defaults == null) {
            return root instanceof YamlConfiguration yaml ? yaml : copy(root);
        }
        YamlConfiguration merged = new YamlConfiguration();
        copyValues(defaults, merged);
        copyValues(root, merged);
        return merged;
    }

    private static YamlConfiguration copy(ConfigurationSection source) {
        YamlConfiguration copy = new YamlConfiguration();
        copyValues(source, copy);
        return copy;
    }

    private static void overlayLegacyDefinitions(ConfigurationSection root,
                                                  Map<String, YamlConfiguration> definitions) {
        ConfigurationSection sickle = root.getConfigurationSection("sickle");
        if (sickle != null) {
            definitions.put("sickle", overlay(definitions.get("sickle"), sickle));
        }
        ConfigurationSection tools = root.getConfigurationSection("harvest-tools.tools");
        if (tools == null) {
            return;
        }
        for (String id : tools.getKeys(false)) {
            ConfigurationSection tool = tools.getConfigurationSection(id);
            if (tool != null) {
                definitions.put(id, overlay(definitions.get(id), tool));
            }
        }
    }

    private static YamlConfiguration overlay(ConfigurationSection base, ConfigurationSection overrides) {
        YamlConfiguration merged = new YamlConfiguration();
        if (base != null) {
            copyRelativeValues(base, merged);
        }
        clearOverriddenSections(overrides, merged);
        copyRelativeValues(overrides, merged);
        return merged;
    }

    private static void clearOverriddenSections(ConfigurationSection source, ConfigurationSection target) {
        // quality-targets is commonly replaced as a complete map by legacy
        // configurations; keep that behavior while allowing scalar overrides
        // in crops, target-chances, and the other nested sections to merge.
        if (source.get("quality-targets") instanceof ConfigurationSection) {
            target.set("quality-targets", null);
        }
    }

    private static void copyRelativeValues(ConfigurationSection source, ConfigurationSection target) {
        String prefix = source.getCurrentPath();
        for (var entry : source.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                continue;
            }
            String path = entry.getKey();
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix + ".")) {
                path = path.substring(prefix.length() + 1);
            }
            target.set(path, entry.getValue());
        }
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.clamp(value, min, max);
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
