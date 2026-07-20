package com.github.cinnaio.materiaengine;

import org.bukkit.configuration.ConfigurationSection;

record MachineGuiLayout(String imageToken, String imageChar, int titleUpdateTicks, int progressImageWidth) {
    static MachineGuiLayout load(ConfigurationSection config, String defaultImageToken, String defaultImageChar,
                                 int defaultTitleUpdateTicks, int defaultProgressImageWidth) {
        String imageToken = config.getString("gui-image-token", config.getString("image-token", defaultImageToken));
        String imageChar = config.getString("gui-image-char", config.getString("image-char", defaultImageChar));
        int titleUpdateTicks = config.getInt("title-update-ticks", defaultTitleUpdateTicks);
        int progressImageWidth = config.getInt("progress-image-width", defaultProgressImageWidth);
        return new MachineGuiLayout(imageToken, imageChar, titleUpdateTicks, progressImageWidth);
    }
}
