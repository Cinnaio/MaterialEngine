package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public record TeaTableRecipe(
        String id,
        Map<String, Input> inputs,
        int processTicks,
        String outputId,
        int outputAmount
) {
    public record Input(String id, int amount, String consumeReplacement) {
    }

    public boolean matches(Map<String, String> slotItemIds) {
        for (Map.Entry<String, Input> entry : inputs.entrySet()) {
            if (!entry.getValue().id().equals(slotItemIds.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsInputAt(String slotKey, String itemId) {
        Input input = inputs.get(slotKey);
        return input != null && input.id().equals(itemId);
    }

    public boolean hasEnough(Map<String, ItemStack> slotItems) {
        for (Map.Entry<String, Input> entry : inputs.entrySet()) {
            ItemStack item = slotItems.get(entry.getKey());
            if (!MachineItems.hasItem(item) || item.getAmount() < entry.getValue().amount()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack createOutput(CraftEngineHook craftEngineHook) {
        return MachineItems.createOutputItem(craftEngineHook, outputId, outputAmount);
    }

    public static TeaTableRecipe load(String id, ConfigurationSection recipe, int defaultProcessTicks) {
        String outputId = recipe.getString("output.id", "");
        ConfigurationSection inputSection = recipe.getConfigurationSection("inputs");
        if (outputId.isBlank() || inputSection == null) {
            return null;
        }
        Map<String, Input> inputs = new LinkedHashMap<>();
        for (String slotKey : inputSection.getKeys(false)) {
            ConfigurationSection slot = inputSection.getConfigurationSection(slotKey);
            if (slot == null) {
                continue;
            }
            String slotId = slot.getString("id", "");
            if (slotId.isBlank()) {
                continue;
            }
            inputs.put(slotKey, new Input(
                    slotId,
                    Math.max(1, slot.getInt("amount", 1)),
                    slot.getString("consume-replacement")
            ));
        }
        if (inputs.isEmpty()) {
            return null;
        }
        return new TeaTableRecipe(
                id,
                inputs,
                Math.max(1, recipe.getInt("process-ticks", defaultProcessTicks)),
                outputId,
                Math.max(1, recipe.getInt("output.amount", 1))
        );
    }
}
