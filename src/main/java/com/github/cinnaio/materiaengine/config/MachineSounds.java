package com.github.cinnaio.materiaengine.config;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;

public final class MachineSounds {
    private static final MachineSounds NONE = new MachineSounds(null, null, null, null, null, null);

    private final SoundSpec open;
    private final SoundSpec close;
    private final SoundSpec start;
    private final SoundSpec finish;
    private final SoundSpec ambient;
    private final SoundSpec fuelConsume;

    private MachineSounds(SoundSpec open, SoundSpec close, SoundSpec start, SoundSpec finish, SoundSpec ambient, SoundSpec fuelConsume) {
        this.open = open;
        this.close = close;
        this.start = start;
        this.finish = finish;
        this.ambient = ambient;
        this.fuelConsume = fuelConsume;
    }

    public static MachineSounds none() {
        return NONE;
    }

    public static MachineSounds load(ConfigurationSection machineConfig) {
        ConfigurationSection sounds = machineConfig.getConfigurationSection("effects.sounds");
        if (sounds == null) {
            return NONE;
        }
        return new MachineSounds(
                spec(sounds, "open"),
                spec(sounds, "close"),
                spec(sounds, "start"),
                spec(sounds, "finish"),
                spec(sounds, "ambient"),
                spec(sounds, "fuel-consume")
        );
    }

    private static SoundSpec spec(ConfigurationSection sounds, String name) {
        ConfigurationSection section = sounds.getConfigurationSection(name);
        if (section == null) {
            return null;
        }
        String key = section.getString("key", "");
        if (key.isBlank()) {
            return null;
        }
        return new SoundSpec(
                key,
                (float) section.getDouble("volume", 1.0),
                (float) section.getDouble("pitch", 1.0),
                Math.max(1, section.getInt("interval", 40))
        );
    }

    public void playOpen(Location location) {
        play(open, location);
    }

    public void playClose(Location location) {
        play(close, location);
    }

    public void playStart(Location location) {
        play(start, location);
    }

    public void playFinish(Location location) {
        play(finish, location);
    }

    public void playFuelConsume(Location location) {
        play(fuelConsume, location);
    }

    public boolean ambientDue(int elapsed) {
        return ambient != null && elapsed % ambient.interval() == 0;
    }

    public void playAmbient(Location location) {
        play(ambient, location);
    }

    private static void play(SoundSpec spec, Location location) {
        if (spec == null || location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, spec.key(), SoundCategory.BLOCKS, spec.volume(), spec.pitch());
    }

    private record SoundSpec(String key, float volume, float pitch, int interval) {
    }
}
