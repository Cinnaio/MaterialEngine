package com.github.cinnaio.materiaengine.config;

import org.bukkit.configuration.ConfigurationSection;

public record BlockStateConfig(String property, String type, int defaultValue, int filledValue, int runningValue) {
    public static BlockStateConfig load(ConfigurationSection config, String defaultProperty, String defaultType,
                                        int defaultValue, int filledValue, int runningValue) {
        return new BlockStateConfig(
                config.getString("block.state.property", defaultProperty),
                config.getString("block.state.type", defaultType),
                config.getInt("block.state.default", defaultValue),
                config.getInt("block.state.filled", filledValue),
                config.getInt("block.state.running", runningValue)
        );
    }

    public boolean booleanType() {
        return type.equalsIgnoreCase("boolean");
    }
}
