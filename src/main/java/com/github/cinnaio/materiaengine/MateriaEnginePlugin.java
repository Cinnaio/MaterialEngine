package com.github.cinnaio.materiaengine;

import com.github.cinnaio.materiaengine.command.ReloadCommand;
import com.github.cinnaio.materiaengine.feature.SickleHarvestFeature;
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
    private SickleHarvestFeature sickleHarvest;
    private StorageExtractionTracker storageExtractionTracker;

    @Override
    public void onEnable() {
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        BeaconEngineBridge beaconEngineBridge = new BeaconEngineBridge(this);
        this.storageExtractionTracker = new StorageExtractionTracker(this, craftEngineHook, beaconEngineBridge);
        this.lang = new MateriaEngineLang(this);
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
        this.sickleHarvest = new SickleHarvestFeature(this, craftEngineHook);
        getServer().getPluginManager().registerEvents(sickleHarvest, this);
        registerCommand("materiaengine", List.of("me"), new ReloadCommand(teaTableGui, processingMachines, sickleHarvest, lang));

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
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("MateriaEngine disabled.");
    }

}
