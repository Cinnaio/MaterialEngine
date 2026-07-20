package com.github.cinnaio.materiaengine;

import org.bukkit.World;

record TeaDryingPanRecipe(
        String id,
        String inputId,
        int inputAmount,
        String weather,
        int processTicks,
        String outputId,
        int outputAmount
) {
    boolean matches(String itemId, World world) {
        return inputId.equals(itemId) && matchesWeather(world);
    }

    boolean acceptsInput(String itemId) {
        return inputId.equals(itemId);
    }

    private boolean matchesWeather(World world) {
        return weather.equals("any") || weather.equals(currentWeather(world));
    }

    private static String currentWeather(World world) {
        if (world == null) {
            return "clear";
        }
        if (world.isThundering()) {
            return "thunder";
        }
        return world.hasStorm() ? "rain" : "clear";
    }
}
