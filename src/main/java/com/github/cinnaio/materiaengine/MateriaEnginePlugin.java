package com.github.cinnaio.materiaengine;

import com.github.cinnaio.materiaengine.command.ReloadCommand;
import com.github.cinnaio.materiaengine.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.materiaengine.feature.TeaTableGui;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MateriaEnginePlugin extends JavaPlugin {
    private TeaTableGui teaTableGui;
    private final List<SimpleProcessingMachineGui> processingMachines = new ArrayList<>();
    private MateriaEngineLang lang;

    @Override
    public void onEnable() {
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        this.lang = new MateriaEngineLang(this);
        this.teaTableGui = new TeaTableGui(this, craftEngineHook, lang,
                "machines.tea-table", "tea_tables", "tea table", "tea-table");
        getServer().getPluginManager().registerEvents(teaTableGui, this);
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.tea-drying-pan", "tea_drying_pans", "tea drying pan", "tea-drying-pan"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.teapan", "teapans", "tea pan", "teapan"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.barrel", "tea_barrels", "tea barrel", "barrel"));
        registerProcessingMachine(new SimpleProcessingMachineGui(this, craftEngineHook, lang,
                "machines.tea-stove", "tea_stoves", "tea stove", "tea-stove"));
        registerCommand("materiaengine", List.of("me"), new ReloadCommand(teaTableGui, processingMachines, lang));

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
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("MateriaEngine disabled.");
    }

}
