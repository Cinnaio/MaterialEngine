package com.github.cinnaio.materiaengine;

import com.github.cinnaio.materiaengine.command.ReloadCommand;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.feature.HarvestToolsFeature;
import com.github.cinnaio.materiaengine.feature.HarvestStats;
import com.github.cinnaio.materiaengine.feature.HarvestReportExporter;
import com.github.cinnaio.materiaengine.feature.HarvestMenu;
import com.github.cinnaio.materiaengine.feature.HarvestMenuItems;
import com.github.cinnaio.materiaengine.feature.SeedPouch;
import com.github.cinnaio.materiaengine.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.materiaengine.feature.TeaTableGui;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.integration.BeaconEngineBridge;
import com.github.cinnaio.materiaengine.integration.StorageExtractionTracker;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MateriaEnginePlugin extends JavaPlugin {
    private TeaTableGui teaTableGui;
    private final List<SimpleProcessingMachineGui> processingMachines = new ArrayList<>();
    private MateriaEngineLang lang;
    private HarvestToolsFeature harvestTools;
    private HarvestStats harvestStats;
    private HarvestMenu harvestMenu;
    private SeedPouch seedPouch;
    private StorageExtractionTracker storageExtractionTracker;

    @Override
    public void onEnable() {
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        BeaconEngineBridge beaconEngineBridge = new BeaconEngineBridge(this);
        this.storageExtractionTracker = new StorageExtractionTracker(this, craftEngineHook, beaconEngineBridge);
        this.lang = new MateriaEngineLang(this);
        this.harvestStats = new HarvestStats(this, HarvestToolsConfig.load(getConfig()).stats());
        this.harvestStats.start();
        this.teaTableGui = new TeaTableGui(this, craftEngineHook, lang,
                "machines.tea-table", "tea_tables", "tea table", "tea-table", storageExtractionTracker);
        getServer().getPluginManager().registerEvents(teaTableGui, this);
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.tea-drying-pan", "tea_drying_pans", "tea drying pan", "tea-drying-pan", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.teapan", "teapans", "tea pan", "teapan", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.barrel", "tea_barrels", "tea barrel", "barrel", storageExtractionTracker));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.tea-stove", "tea_stoves", "tea stove", "tea-stove", storageExtractionTracker));
        this.harvestTools = new HarvestToolsFeature(this, craftEngineHook, beaconEngineBridge, lang, harvestStats);
        this.seedPouch = new SeedPouch(this, craftEngineHook, lang, new HarvestMenuItems(craftEngineHook), harvestTools::settings);
        harvestTools.setSeedPouch(seedPouch);
        getServer().getPluginManager().registerEvents(seedPouch, this);
        getServer().getPluginManager().registerEvents(harvestTools, this);
        this.harvestMenu = new HarvestMenu(this, harvestStats, lang, new HarvestMenuItems(craftEngineHook), harvestTools::settings);
        getServer().getPluginManager().registerEvents(harvestMenu, this);
        registerCommand("materiaengine", List.of("me"), new ReloadCommand(teaTableGui, processingMachines, harvestTools, lang,
                harvestStats, new HarvestReportExporter(this, harvestStats, lang), harvestMenu, seedPouch));

        getLogger().info("MateriaEngine enabled.");
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
        getLogger().info("MateriaEngine disabled.");
    }

}
