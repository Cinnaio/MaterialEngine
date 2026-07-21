package com.github.cinnaio.materiaengine.command;

import com.github.cinnaio.materiaengine.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.materiaengine.feature.StaticMachineGui;
import com.github.cinnaio.materiaengine.feature.TeaDryingPanGui;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collection;
import java.util.List;

public final class ReloadCommand implements BasicCommand {
    private final TeaDryingPanGui teaDryingPanGui;
    private final StaticMachineGui teaTableGui;
    private final List<SimpleProcessingMachineGui> processingMachines;
    private final MateriaEngineLang lang;

    public ReloadCommand(TeaDryingPanGui teaDryingPanGui, StaticMachineGui teaTableGui, List<SimpleProcessingMachineGui> processingMachines, MateriaEngineLang lang) {
        this.teaDryingPanGui = teaDryingPanGui;
        this.teaTableGui = teaTableGui;
        this.processingMachines = processingMachines;
        this.lang = lang;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            teaDryingPanGui.save();
            processingMachines.forEach(SimpleProcessingMachineGui::save);
            lang.reload();
            teaDryingPanGui.reload();
            teaTableGui.reload();
            processingMachines.forEach(SimpleProcessingMachineGui::reload);
            source.getSender().sendMessage(lang.text(source.getSender(), "command.reload-success"));
            return;
        }
        source.getSender().sendMessage(lang.text(source.getSender(), "command.usage-reload"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 ? List.of("reload") : List.of();
    }

    @Override
    public String permission() {
        return "materiaengine.admin";
    }
}
