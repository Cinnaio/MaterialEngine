package com.github.cinnaio.materiaengine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

record SimpleMachineRecipe(
        String id,
        String inputId,
        int inputAmount,
        int processTicks,
        String outputId,
        int outputAmount,
        int outputState
) {
    boolean matches(String itemId) {
        return inputId.equals(itemId);
    }

    boolean acceptsInput(String itemId) {
        return inputId.equals(itemId);
    }

    static SimpleMachineRecipe load(String id, ConfigurationSection recipe, int defaultProcessTicks, int defaultOutputState) {
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

    ItemStack createOutput(CraftEngineHook craftEngineHook) {
        return MachineItems.createOutputItem(craftEngineHook, outputId, outputAmount);
    }
}
