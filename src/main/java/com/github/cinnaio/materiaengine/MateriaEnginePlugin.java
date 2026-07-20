package com.github.cinnaio.materiaengine;

import com.github.cinnaio.materiaengine.command.ReloadCommand;
import com.github.cinnaio.materiaengine.data.TeaDryingPanDataStore;
import com.github.cinnaio.materiaengine.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.materiaengine.feature.TeaDryingPanGui;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MateriaEnginePlugin extends JavaPlugin {
    private TeaDryingPanGui teaDryingPanGui;
    private final List<SimpleProcessingMachineGui> processingMachines = new ArrayList<>();
    private MateriaEngineLang lang;

    @Override
    public void onEnable() {
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        TeaDryingPanDataStore dataStore = new TeaDryingPanDataStore(this);
        this.lang = new MateriaEngineLang(this);
        this.teaDryingPanGui = new TeaDryingPanGui(this, craftEngineHook, dataStore, lang);
        getServer().getPluginManager().registerEvents(teaDryingPanGui, this);
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.teapan", "teapans", "tea pan", "teapan"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.barrel", "tea_barrels", "tea barrel", "barrel"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.tea-stove", "tea_stoves", "tea stove", "tea-stove"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.cooking-pan", "cooking_pans", "cooking pan", "cooking-pan"));
        registerCommand("materiaengine", List.of("me"), new ReloadCommand(teaDryingPanGui, processingMachines, lang));

        getLogger().info("MateriaEngine enabled.");
    }

    private void registerProcessingMachine(SimpleProcessingMachineGui gui) {
        processingMachines.add(gui);
        getServer().getPluginManager().registerEvents(gui, this);
    }

    @Override
    public void onDisable() {
        if (teaDryingPanGui != null) {
            teaDryingPanGui.shutdown();
        }
        processingMachines.forEach(SimpleProcessingMachineGui::shutdown);
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("MateriaEngine disabled.");
    }

}
