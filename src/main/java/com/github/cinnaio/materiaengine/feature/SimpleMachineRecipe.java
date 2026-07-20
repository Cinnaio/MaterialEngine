package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public record SimpleMachineRecipe(
        String id,
        String inputId,
        int inputAmount,
        int processTicks,
        String outputId,
        int outputAmount,
        int outputState
) {
    public boolean matches(String itemId) {
        return inputId.equals(itemId);
    }

    public boolean acceptsInput(String itemId) {
        return inputId.equals(itemId);
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
