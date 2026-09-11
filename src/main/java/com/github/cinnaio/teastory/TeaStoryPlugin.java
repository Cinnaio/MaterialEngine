package com.github.cinnaio.teastory;

import com.github.cinnaio.teastory.command.ReloadCommand;
import com.github.cinnaio.teastory.config.DefinitionFiles;
import com.github.cinnaio.teastory.config.HarvestToolsConfig;
import com.github.cinnaio.teastory.feature.HarvestToolsFeature;
import com.github.cinnaio.teastory.feature.HarvestStats;
import com.github.cinnaio.teastory.feature.HarvestReportExporter;
import com.github.cinnaio.teastory.feature.HarvestMenu;
import com.github.cinnaio.teastory.feature.HarvestMenuItems;
import com.github.cinnaio.teastory.feature.SeedPouch;
import com.github.cinnaio.teastory.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.teastory.feature.TeaTableGui;
import com.github.cinnaio.teastory.i18n.TeaStoryLang;
import com.github.cinnaio.teastory.integration.BeaconEngineBridge;
import com.github.cinnaio.teastory.integration.StorageExtractionTracker;
import com.github.cinnaio.teastory.util.CraftEngineHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class TeaStoryPlugin extends JavaPlugin {
    private DefinitionFiles definitions;
    private TeaTableGui teaTableGui;
    private final List<SimpleProcessingMachineGui> processingMachines = new ArrayList<>();
    private TeaStoryLang lang;
    private HarvestToolsFeature harvestTools;
    private HarvestStats harvestStats;
    private HarvestMenu harvestMenu;
    private SeedPouch seedPouch;
    private StorageExtractionTracker storageExtractionTracker;

    @Override
    public void onEnable() {
        this.definitions = new DefinitionFiles(this);
        this.definitions.prepare();
        reloadConfig();
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        BeaconEngineBridge beaconEngineBridge = new BeaconEngineBridge(this);
        this.storageExtractionTracker = new StorageExtractionTracker(this, craftEngineHook, beaconEngineBridge);
        this.lang = new TeaStoryLang(this);
        this.harvestStats = new HarvestStats(this, HarvestToolsConfig.load(getConfig(), definitions.loadTools()).stats());
        this.harvestStats.start();
        this.teaTableGui = new TeaTableGui(this, craftEngineHook, lang,
                definitions, "tea-table", "tea_tables", "tea table", "tea-table", storageExtractionTracker);
        getServer().getPluginManager().registerEvents(teaTableGui, this);
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                definitions, "tea-drying-pan", "tea_drying_pans", "tea drying pan", "tea-drying-pan", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                definitions, "teapan", "teapans", "tea pan", "teapan", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                definitions, "barrel", "tea_barrels", "tea barrel", "barrel", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                definitions, "tea-stove", "tea_stoves", "tea stove", "tea-stove", storageExtractionTracker));
        this.harvestTools = new HarvestToolsFeature(this, craftEngineHook, beaconEngineBridge, lang, harvestStats, definitions);
        this.seedPouch = new SeedPouch(this, craftEngineHook, lang, new HarvestMenuItems(craftEngineHook), harvestTools::settings);
        harvestTools.setSeedPouch(seedPouch);
        getServer().getPluginManager().registerEvents(seedPouch, this);
        getServer().getPluginManager().registerEvents(harvestTools, this);
        this.harvestMenu = new HarvestMenu(this, harvestStats, lang, new HarvestMenuItems(craftEngineHook), harvestTools::settings);
        getServer().getPluginManager().registerEvents(harvestMenu, this);
        registerCommand("teastory", List.of("ts", "me", "materiaengine"), new ReloadCommand(teaTableGui, processingMachines, harvestTools, lang,
                harvestStats, new HarvestReportExporter(this, harvestStats, lang), harvestMenu, seedPouch));

        getLogger().info("TeaStory enabled.");
    }

    private void registerProcessingMachine(SimpleProcessingMachineGui gui) {
        processingMachines.add(gui);
        getServer().getPluginManager().registerEvents(gui, this);
    }

    @Override
    public void onDisable() {
        if (teaTableGui != null) {
            teaTableGui.shutdown();
        }
        processingMachines.forEach(SimpleProcessingMachineGui::shutdown);
        if (storageExtractionTracker != null) {
            storageExtractionTracker.shutdown();
        }
        if (harvestTools != null) harvestTools.shutdown();
        if (harvestMenu != null) harvestMenu.shutdown();
        if (seedPouch != null) seedPouch.shutdown();
        if (harvestStats != null) harvestStats.shutdown();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("TeaStory disabled.");
    }

}
