package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TeaTableRecipe(
        String id,
        Map<String, Input> inputs,
        int processTicks,
        String outputId,
        int outputAmount
) {
    public record Variant(String id, String consumeReplacement) {
    }

    public record Input(List<Variant> variants, int amount) {
        public boolean accepts(String itemId) {
            if (itemId == null) {
                return false;
            }
            for (Variant variant : variants) {
                if (variant.id().equals(itemId)) {
                    return true;
                }
            }
            return false;
        }

        public String replacementFor(String itemId) {
            if (itemId == null) {
                return null;
            }
            for (Variant variant : variants) {
                if (variant.id().equals(itemId)) {
                    return variant.consumeReplacement();
                }
            }
            return null;
        }
    }

    public boolean matches(Map<String, String> slotItemIds) {
        for (Map.Entry<String, Input> entry : inputs.entrySet()) {
            if (!entry.getValue().accepts(slotItemIds.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsInputAt(String slotKey, String itemId) {
        Input input = inputs.get(slotKey);
        return input != null && input.accepts(itemId);
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
            Input input = loadInput(slot);
            if (input != null) {
                inputs.put(slotKey, input);
            }
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

    private static Input loadInput(ConfigurationSection slot) {
        List<Variant> variants = new ArrayList<>();
        for (Map<?, ?> entry : slot.getMapList("any")) {
            Object variantId = entry.get("id");
            if (variantId == null || variantId.toString().isBlank()) {
                continue;
            }
            Object replacement = entry.get("consume-replacement");
            variants.add(new Variant(variantId.toString(), replacement == null ? null : replacement.toString()));
        }
        if (variants.isEmpty()) {
            String sharedReplacement = slot.getString("consume-replacement");
            List<String> ids = slot.isList("id") ? slot.getStringList("id") : List.of(slot.getString("id", ""));
            for (String variantId : ids) {
                if (variantId != null && !variantId.isBlank()) {
                    variants.add(new Variant(variantId, sharedReplacement));
                }
            }
        }
        if (variants.isEmpty()) {
            return null;
        }
        return new Input(List.copyOf(variants), Math.max(1, slot.getInt("amount", 1)));
    }
}
