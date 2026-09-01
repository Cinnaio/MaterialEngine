package com.github.cinnaio.materiaengine.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional bridge to BeaconEngine's service API. MateriaEngine remains
 * standalone when BeaconEngine is absent or provides an older API.
 */
public final class BeaconEngineBridge {
    private static final String BEACON_PLUGIN_NAME = "BeaconEngine";
    private static final String API_CLASS_NAME = "com.github.cinnaio.beaconengine.api.BeaconEngineAPI";

    private final JavaPlugin plugin;
    private Plugin boundPlugin;
    private Object api;
    private Method recordItemObtained;
    private boolean lookupFailureLogged;
    private boolean invocationFailureLogged;

    public BeaconEngineBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordItemObtained(Player player, String itemId, long amount) {
        if (player == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }
        Plugin beaconEngine = Bukkit.getPluginManager().getPlugin(BEACON_PLUGIN_NAME);
        if (beaconEngine == null || !beaconEngine.isEnabled()) {
            return;
        }
        if (this.boundPlugin != beaconEngine) {
            clearBinding();
        }
        if (!resolve(beaconEngine)) {
            return;
        }
        try {
            this.recordItemObtained.invoke(this.api, player, itemId, amount);
        } catch (InvocationTargetException error) {
            clearBinding();
            logInvocationFailure(error.getCause() == null ? error : error.getCause());
        } catch (ReflectiveOperationException | RuntimeException error) {
            clearBinding();
            logInvocationFailure(error);
        }
    }

    private boolean resolve(Plugin beaconEngine) {
        if (this.boundPlugin == beaconEngine && this.api != null && this.recordItemObtained != null) {
            return true;
        }
        try {
            ClassLoader classLoader = beaconEngine.getClass().getClassLoader();
            Class<?> apiClass = classLoader == null
                    ? Class.forName(API_CLASS_NAME)
                    : Class.forName(API_CLASS_NAME, true, classLoader);
            ServicesManager services = Bukkit.getServicesManager();
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object service = services.load((Class) apiClass);
            if (service == null) {
                return false;
            }
            Method method = apiClass.getMethod("recordItemObtained", Player.class, String.class, long.class);
            this.boundPlugin = beaconEngine;
            this.api = service;
            this.recordItemObtained = method;
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            logLookupFailure(error);
            return false;
        }
    }

    private void clearBinding() {
        this.boundPlugin = null;
        this.api = null;
        this.recordItemObtained = null;
    }

    private void logLookupFailure(Throwable error) {
        if (this.lookupFailureLogged) {
            return;
        }
        this.lookupFailureLogged = true;
        this.plugin.getLogger().warning("[MateriaEngine] BeaconEngine integration is unavailable: " + error.getMessage());
    }

    private void logInvocationFailure(Throwable error) {
        if (this.invocationFailureLogged) {
            return;
        }
        this.invocationFailureLogged = true;
        this.plugin.getLogger().warning("[MateriaEngine] Failed to record a BeaconEngine item acquisition: " + error.getMessage());
    }
}
