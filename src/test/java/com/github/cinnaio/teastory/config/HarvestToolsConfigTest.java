package com.github.cinnaio.teastory.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HarvestToolsConfigTest {
    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void coversFourteenCustomCropsFiveVanillaCropsAndSixTrees() {
        var config = HarvestToolsConfig.load(defaults());
        assertEquals(19, config.crops().size());
        assertEquals(4, config.tools().size());
        assertEquals(6, config.fruits().size());
        assertEquals(7, config.crops().get("cgap:xian_rice_plant_crop").matureAge());
        assertEquals(3, config.crops().get("cgap:xian_rice_seed_crop").matureAge());
        assertEquals(4, config.crops().get("cgap:lotus_crop").matureAge());
    }

    @Test
    void existingConfigInheritsNewToolsAndCropsWithoutOverwritingOverrides() throws Exception {
        var existing = new YamlConfiguration();
        existing.loadFromString("sickle:\n  radius: 2\n  crops:\n    cgap:mint_crop:\n      seed: ''\n");
        existing.setDefaults(defaults());
        var config = HarvestToolsConfig.load(existing);
        assertEquals(2, config.radius());
        assertEquals(4, config.tools().size());
        assertTrue(config.crops().containsKey("cgap:ginger_crop"));
        assertFalse(config.crops().containsKey("cgap:mint_crop"));
    }

    @Test
    void qualityNeverChangesSeedsOrAlreadyHighGradeLeaves() {
        var tool = HarvestToolsConfig.load(defaults()).tools().get("cgap:tea_shears");
        for (int seed = 0; seed < 100; seed++) {
            assertEquals("cgap:tea_seeds", tool.replacement("cgap:tea_seeds", new Random(seed)));
            assertEquals("cgap:fresh_tea_leaf_bud", tool.replacement("cgap:fresh_tea_leaf_bud", new Random(seed)));
            assertEquals("cgap:fresh_tea_leaf_bud_leaf1", tool.replacement("cgap:fresh_tea_leaf_bud_leaf1", new Random(seed)));
        }
    }

    @Test
    void guaranteedQualityUpgradeProducesOnlyConfiguredPremiumLeaves() {
        var yaml = defaults();
        yaml.set("harvest-tools.tools.tea-shears.chance", 1);
        var tool = HarvestToolsConfig.load(yaml).tools().get("cgap:tea_shears");
        var outcomes = new java.util.HashSet<String>();
        Random random = new Random(15);
        for (int i = 0; i < 100; i++) outcomes.add(tool.replacement("cgap:fresh_tea_leaf_old_leaf", random));
        assertEquals(tool.qualityTargets().keySet(), outcomes);
    }

    @Test
    void clampsUnboundedSettingsAndRejectsDuplicateToolIds() {
        var yaml = defaults();
        yaml.set("sickle.radius", 999);
        yaml.set("sickle.cooldown-ticks", 999);
        yaml.set("sickle.durability-cost", -1);
        yaml.set("harvest-tools.cooldown-ticks", -1);
        yaml.set("harvest-tools.durability-cost", 999);
        yaml.set("harvest-tools.tools.fruit-picker.reach", 100);
        yaml.set("harvest-tools.tools.herb-shears.chance", Double.NaN);
        yaml.set("harvest-tools.regrow-seconds", -1);
        var config = HarvestToolsConfig.load(yaml);
        assertEquals(2, config.radius());
        assertEquals(200, config.sickleCooldownTicks());
        assertEquals(0, config.sickleDurabilityCost());
        assertEquals(0, config.tools().get("cgap:herb_shears").cooldownTicks());
        assertEquals(100, config.tools().get("cgap:herb_shears").durabilityCost());
        assertEquals(6, config.tools().get("cgap:fruit_picker").reach());
        assertEquals(0, config.tools().get("cgap:herb_shears").chance());
        assertEquals(60, config.regrowSeconds());
        yaml.set("harvest-tools.tools.herb-shears.item", "cgap:tea_shears");
        assertThrows(IllegalArgumentException.class, () -> HarvestToolsConfig.load(yaml));
    }

    @Test
    void feedbackAndStatsDefaultToEnabledWithBoundedEffects() {
        var config = HarvestToolsConfig.load(defaults());
        assertTrue(config.feedback().enabled());
        assertTrue(config.feedback().actionbar());
        assertEquals("HAPPY_VILLAGER", config.feedback().success().particle());
        assertEquals(4, config.feedback().success().count());
        assertTrue(config.stats().enabled());
        assertFalse(config.stats().includeCreative());
        assertEquals(30, config.stats().flushSeconds());
        assertEquals(4, config.tools().get("cgap:herb_shears").cooldownTicks());
        assertEquals(1, config.tools().get("cgap:herb_shears").durabilityCost());
    }

    @Test
    void disablingToolsKeepsExistingSickleButDisablesBasket() {
        var yaml = defaults();
        yaml.set("harvest-tools.enabled", false);
        var config = HarvestToolsConfig.load(yaml);
        assertTrue(config.tools().isEmpty());
        assertTrue(config.basketItem().isEmpty());
        assertFalse(config.crops().isEmpty());
    }

    @Test
    void perCropChancesAndPerTreeRecoveryOverrideDefaults() {
        var yaml = defaults();
        yaml.set("harvest-tools.tools.herb-shears.target-chances.cgap:mint_crop", 0);
        yaml.set("harvest-tools.tools.herb-shears.bonus-amount", 3);
        yaml.set("harvest-tools.regrow-overrides.cgap:peach_leaves", 1800);
        var config = HarvestToolsConfig.load(yaml);
        var tool = config.tools().get("cgap:herb_shears");
        assertEquals(0, tool.chanceFor("cgap:mint_crop"));
        assertEquals(.2, tool.chanceFor("cgap:roselle_crop"));
        assertEquals(3, tool.bonusAmount());
        assertEquals(1800, config.regrowSeconds("cgap:peach_leaves"));
        assertEquals(1200, config.regrowSeconds("cgap:lemon_leaves"));
        yaml.set("harvest-tools.tools.tea-shears.target-chances.cgap:tea_tree_crop", 0);
        var tea = HarvestToolsConfig.load(yaml).tools().get("cgap:tea_shears");
        assertEquals("cgap:fresh_tea_leaf_old_leaf", tea.replacement("cgap:fresh_tea_leaf_old_leaf", "cgap:tea_tree_crop", new Random(1)));
    }

    @Test
    void effectsAndPersistenceIntervalsAreBoundedAndInvalidParticlesAreRejected() {
        var yaml = defaults();
        yaml.set("harvest-tools.feedback.success.volume", Double.NaN);
        yaml.set("harvest-tools.feedback.success.count", 100000);
        yaml.set("harvest-tools.feedback.success.offset", -1);
        yaml.set("harvest-tools.stats.flush-seconds", 0);
        yaml.set("harvest-tools.tools.herb-shears.bonus-amount", 100000);
        var config = HarvestToolsConfig.load(yaml);
        assertEquals(.65f, config.feedback().success().volume());
        assertEquals(64, config.feedback().success().count());
        assertEquals(0, config.feedback().success().offset());
        assertEquals(5, config.stats().flushSeconds());
        assertEquals(64, config.tools().get("cgap:herb_shears").bonusAmount());
        yaml.set("harvest-tools.feedback.success.particle", "BLOCK");
        assertThrows(IllegalArgumentException.class, () -> HarvestToolsConfig.load(yaml));
        yaml.set("harvest-tools.feedback.success.particle", "NOT_A_PARTICLE");
        assertThrows(IllegalArgumentException.class, () -> HarvestToolsConfig.load(yaml));
    }
}
