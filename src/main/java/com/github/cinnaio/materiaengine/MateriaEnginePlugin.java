package com.github.cinnaio.materiaengine;

import org.bukkit.plugin.java.JavaPlugin;

public final class MateriaEnginePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        TeaDryingPanGui teaDryingPanGui = new TeaDryingPanGui(this);
        getServer().getPluginManager().registerEvents(teaDryingPanGui, this);

        getLogger().info("MateriaEngine enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MateriaEngine disabled.");
    }
}
