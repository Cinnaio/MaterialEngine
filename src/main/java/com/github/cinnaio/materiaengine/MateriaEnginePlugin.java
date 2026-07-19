package com.github.cinnaio.materiaengine;

import io.papermc.paper.command.brigadier.BasicCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MateriaEnginePlugin extends JavaPlugin {
    private TeaDryingPanGui teaDryingPanGui;

    @Override
    public void onEnable() {
        CraftEngineHook craftEngineHook = new CraftEngineHook();
        TeaDryingPanDataStore dataStore = new TeaDryingPanDataStore(this);
        this.teaDryingPanGui = new TeaDryingPanGui(this, craftEngineHook, dataStore);
        getServer().getPluginManager().registerEvents(teaDryingPanGui, this);
        registerCommand("materiaengine", List.of("me"), new ReloadCommand());

        getLogger().info("MateriaEngine enabled.");
    }

    @Override
    public void onDisable() {
        if (teaDryingPanGui != null) {
            teaDryingPanGui.shutdown();
        }
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("MateriaEngine disabled.");
    }

    private final class ReloadCommand implements BasicCommand {
        @Override
        public void execute(io.papermc.paper.command.brigadier.CommandSourceStack source, String[] args) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                teaDryingPanGui.save();
                teaDryingPanGui.reload();
                source.getSender().sendMessage("§a[MateriaEngine] 配置已重载。");
                return;
            }
            source.getSender().sendMessage("§e用法: /materiaengine reload");
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
