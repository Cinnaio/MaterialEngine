package com.github.cinnaio.materiaengine;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;

final class TeaDryingPanDataStore {
    private final MachineDataStore<TeaDryingPanMachine> delegate;

    TeaDryingPanDataStore(JavaPlugin plugin) {
        this.delegate = new MachineDataStore<>(plugin, "tea_drying_pans", "tea drying pan", row -> new TeaDryingPanMachine(
                row.worldId(),
                row.x(),
                row.y(),
                row.z(),
                row.contents(),
                row.running(),
                row.elapsed(),
                row.runningRecipeId()
        ));
    }

    Map<String, TeaDryingPanMachine> load() {
        return delegate.load();
    }

    void save(Collection<TeaDryingPanMachine> machines) {
        delegate.save(machines);
    }

    void delete(String key) {
        delegate.delete(key);
    }
}
