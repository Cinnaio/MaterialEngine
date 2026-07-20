package com.github.cinnaio.materiaengine.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;

public final class TeaDryingPanDataStore {
    private final MachineDataStore<TeaDryingPanMachine> delegate;

    public TeaDryingPanDataStore(JavaPlugin plugin) {
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

    public Map<String, TeaDryingPanMachine> load() {
        return delegate.load();
    }

    public void save(Collection<TeaDryingPanMachine> machines) {
        delegate.save(machines);
    }

    public void delete(String key) {
        delegate.delete(key);
    }
}
