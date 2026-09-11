package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.i18n.TeaStoryLang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HarvestReportExporter {
    private final JavaPlugin plugin;
    private final HarvestStats stats;
    private final TeaStoryLang lang;
    private final AtomicBoolean running = new AtomicBoolean();

    public HarvestReportExporter(JavaPlugin plugin, HarvestStats stats, TeaStoryLang lang) {
        this.plugin = plugin;
        this.stats = stats;
        this.lang = lang;
    }

    public void start(CommandSender sender, UUID player, HarvestPeriod period) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage(lang.text(sender, "harvest.export.busy"));
            return;
        }
        sender.sendMessage(lang.text(sender, "harvest.export.started"));
        if (!stats.persistenceReady()) sender.sendMessage(lang.text(sender, "harvest.stats.persistence-unavailable"));
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    String file = stats.exportCsv(player, period).getFileName().toString();
                    reply(sender, () -> sender.sendMessage(lang.text(sender, "harvest.export.saved").replace("{file}", file)));
                } catch (IOException | RuntimeException error) {
                    plugin.getLogger().warning("Harvest export failed: " + error.getMessage());
                    reply(sender, () -> sender.sendMessage(lang.text(sender, "harvest.export.failed")));
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException error) {
            running.set(false);
            throw error;
        }
    }

    private void reply(CommandSender sender, Runnable message) {
        if (!plugin.isEnabled()) return;
        if (sender instanceof Player player) player.getScheduler().run(plugin, task -> message.run(), null);
        else Bukkit.getGlobalRegionScheduler().execute(plugin, message);
    }
}
