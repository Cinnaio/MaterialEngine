package com.github.cinnaio.materiaengine.config;

import org.bukkit.configuration.ConfigurationSection;

public record MachineGuiLayout(String imageToken, String titleTemplate, int titleUpdateTicks, int progressImageWidth) {
    public static MachineGuiLayout load(ConfigurationSection config, String defaultImageToken,
                                 int defaultTitleUpdateTicks, int defaultProgressImageWidth) {
        String imageToken = string(config, "gui.image-token", string(config, "gui.gui-image-token",
                config.getString("gui-image-token", config.getString("image-token", defaultImageToken))));
        String titleTemplate = string(config, "gui.title", config.getString("title", ""));
        int titleUpdateTicks = integer(config, "gui.title-update-ticks", config.getInt("title-update-ticks", defaultTitleUpdateTicks));
        int progressImageWidth = integer(config, "gui.progress-image-width", config.getInt("progress-image-width", defaultProgressImageWidth));
        return new MachineGuiLayout(imageToken, titleTemplate, titleUpdateTicks, progressImageWidth);
    }

    private static String string(ConfigurationSection config, String path, String fallback) {
        return config.isString(path) ? config.getString(path, fallback) : fallback;
    }

    private static int integer(ConfigurationSection config, String path, int fallback) {
        return config.isInt(path) ? config.getInt(path) : fallback;
    }
}
