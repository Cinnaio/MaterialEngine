package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public record SimpleMachineRecipe(
        String id,
        String inputId,
        int inputAmount,
        String weather,
        int processTicks,
        String outputId,
        int outputAmount,
        int outputState
) {
    public boolean matches(String itemId, World world) {
        return inputId.equals(itemId) && matchesWeather(world);
    }

    public boolean acceptsInput(String itemId) {
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

    public static SimpleMachineRecipe load(String id, ConfigurationSection recipe, int defaultProcessTicks, int defaultOutputState) {
        String inputId = recipe.getString("input.id", "");
        String outputId = recipe.getString("output.id", "");
        if (inputId.isBlank() || outputId.isBlank()) {
            return null;
        }
        return new SimpleMachineRecipe(
                id,
                inputId,
                Math.max(1, recipe.getInt("input.amount", 1)),
                recipe.getString("conditions.weather", "any").toLowerCase(),
                recipe.getInt("process-ticks", defaultProcessTicks),
                outputId,
                Math.max(1, recipe.getInt("output.amount", 1)),
                recipe.getInt("output-state", defaultOutputState)
        );
    }

    public ItemStack createOutput(CraftEngineHook craftEngineHook) {
        return MachineItems.createOutputItem(craftEngineHook, outputId, outputAmount);
    }
}
