package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaticMachineGui implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final CraftEngineHook craftEngineHook;
    private final MateriaEngineLang lang;
    private final String configPath;
    private final String langPrefix;
    private String blockId;
    private String imageToken;
    private String titleTemplate;

    public StaticMachineGui(JavaPlugin plugin, CraftEngineHook craftEngineHook, MateriaEngineLang lang,
                            String configPath, String langPrefix) {
        this.plugin = plugin;
        this.craftEngineHook = craftEngineHook;
        this.lang = lang;
        this.configPath = configPath;
        this.langPrefix = langPrefix;
        reload();
    }

    public void reload() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(configPath);
        if (config == null) {
            throw new IllegalStateException("Missing " + configPath + " config");
        }
        this.blockId = string(config, "block.id", config.getString("block-id", ""));
        this.imageToken = string(config, "gui.image-token", config.getString("gui-image-token", config.getString("image-token", "")));
        this.titleTemplate = string(config, "gui.title", config.getString("title", ""));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!craftEngineHook.isCustomBlock(event.getClickedBlock(), blockId)) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler
    void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, title(player));
        player.openInventory(inventory);
    }

    private Component title(Player player) {
        String template = titleTemplate.isBlank() ? lang.text(player, langPrefix + ".title") : titleTemplate;
        String parsed = template
                .replace("{image}", imageToken)
                .replace("{name}", lang.text(player, langPrefix + ".name"))
                .replace("<shift:-11>", "")
                .replace("<shift:-8>", "");
        return parsed.contains("<") ? MINI_MESSAGE.deserialize(parsed) : LEGACY_SERIALIZER.deserialize(parsed);
    }

    private static String string(ConfigurationSection config, String path, String fallback) {
        return config.isString(path) ? config.getString(path, fallback) : fallback;
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
