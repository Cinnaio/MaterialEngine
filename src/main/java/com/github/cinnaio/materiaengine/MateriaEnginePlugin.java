package com.github.cinnaio.materiaengine;

import io.papermc.paper.command.brigadier.BasicCommand;
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
        registerCommand("materiaengine", List.of("me"), new ReloadCommand());

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

    private final class ReloadCommand implements BasicCommand {
        @Override
        public void execute(io.papermc.paper.command.brigadier.CommandSourceStack source, String[] args) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                teaDryingPanGui.save();
                processingMachines.forEach(SimpleProcessingMachineGui::save);
                lang.reload();
                teaDryingPanGui.reload();
                processingMachines.forEach(SimpleProcessingMachineGui::reload);
                source.getSender().sendMessage(lang.text(source.getSender(), "command.reload-success"));
                return;
            }
            source.getSender().sendMessage(lang.text(source.getSender(), "command.usage-reload"));
        }

        @Override
        public java.util.Collection<String> suggest(io.papermc.paper.command.brigadier.CommandSourceStack source, String[] args) {
            return args.length <= 1 ? List.of("reload") : List.of();
        }

        @Override
        public String permission() {
            return "materiaengine.admin";
        }
    }
}
