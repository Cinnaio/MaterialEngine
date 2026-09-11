package com.github.cinnaio.teastory.command;

import com.github.cinnaio.teastory.feature.HarvestToolsFeature;
import com.github.cinnaio.teastory.feature.HarvestStats;
import com.github.cinnaio.teastory.feature.HarvestPeriod;
import com.github.cinnaio.teastory.feature.HarvestReportExporter;
import com.github.cinnaio.teastory.feature.HarvestMenu;
import com.github.cinnaio.teastory.feature.HarvestAccess;
import com.github.cinnaio.teastory.feature.SeedPouch;
import com.github.cinnaio.teastory.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.teastory.feature.TeaTableGui;
import com.github.cinnaio.teastory.i18n.TeaStoryLang;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReloadCommand implements BasicCommand {
    private final TeaTableGui teaTableGui;
    private final List<SimpleProcessingMachineGui> processingMachines;
    private final HarvestToolsFeature harvestTools;
    private final TeaStoryLang lang;
    private final HarvestStats stats;
    private final HarvestReportExporter exporter;
    private final HarvestMenu menu;
    private final SeedPouch pouch;

    public ReloadCommand(TeaTableGui teaTableGui, List<SimpleProcessingMachineGui> processingMachines,
                         HarvestToolsFeature harvestTools, TeaStoryLang lang, HarvestStats stats,
                         HarvestReportExporter exporter, HarvestMenu menu, SeedPouch pouch) {
        this.teaTableGui = teaTableGui;
        this.processingMachines = processingMachines;
        this.harvestTools = harvestTools;
        this.lang = lang;
        this.stats = stats;
        this.exporter = exporter;
        this.menu = menu;
        this.pouch = pouch;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 2 && args[0].equalsIgnoreCase("harvest") && args[1].equalsIgnoreCase("pouch")) {
            if (sender instanceof Player player) {
                if (allowed(sender, HarvestAccess.read(sender, player.getUniqueId()))) pouch.openFirst(player);
            } else sender.sendMessage(lang.text(sender, "command.player-only"));
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("harvest") && (args.length == 1
                || args[1].equalsIgnoreCase("menu") && args.length <= 3)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(lang.text(sender, "command.player-only"));
                return;
            }
            Target target = resolveTarget(sender, args.length == 3 ? args[2] : null);
            if (target != null && allowed(sender, HarvestAccess.read(sender, target.player()))) menu.open(player, target.player(), target.name());
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!allowed(sender, HarvestAccess.admin(sender))) return;
            teaTableGui.save();
            processingMachines.forEach(SimpleProcessingMachineGui::save);
            lang.reload();
            teaTableGui.reload();
            processingMachines.forEach(SimpleProcessingMachineGui::reload);
            boolean harvestReloaded = harvestTools.reload();
            source.getSender().sendMessage(lang.text(source.getSender(), harvestReloaded
                    ? "command.reload-success" : "command.reload-partial"));
            return;
        }
        if (args.length >= 2 && args.length <= 4 && args[0].equalsIgnoreCase("harvest")
                && (args[1].equalsIgnoreCase("stats") || args[1].equalsIgnoreCase("export"))) {
            if (args[1].equalsIgnoreCase("export") && !allowed(sender, HarvestAccess.admin(sender))) return;
            String requested = args.length >= 3 ? args[2] : null;
            HarvestPeriod period = HarvestPeriod.ALL;
            if (args.length == 3 && (args[2].equalsIgnoreCase("today") || args[2].equalsIgnoreCase("week"))) {
                period = HarvestPeriod.valueOf(args[2].toUpperCase(Locale.ROOT));
                requested = null;
            } else if (args.length == 4) {
                try { period = HarvestPeriod.valueOf(args[3].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) {
                    source.getSender().sendMessage(lang.text(source.getSender(), "harvest.stats.invalid-period"));
                    return;
                }
            }
            Target target = resolveTarget(source.getSender(), requested);
            if (target == null) return;
            if (!allowed(sender, HarvestAccess.read(sender, target.player()))) return;
            if (args[1].equalsIgnoreCase("export")) exporter.start(source.getSender(), target.player(), period);
            else showHarvestStats(source.getSender(), target, period);
            return;
        }
        source.getSender().sendMessage(lang.text(source.getSender(), "command.usage-reload"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        boolean admin = HarvestAccess.admin(source.getSender());
        boolean others = HarvestAccess.others(source.getSender());
        if (args.length <= 1) return admin ? List.of("reload", "harvest") : List.of("harvest");
        if (!args[0].equalsIgnoreCase("harvest")) return List.of();
        if (args.length == 2) return admin ? List.of("menu", "stats", "pouch", "export") : List.of("menu", "stats", "pouch");
        if (args[1].equalsIgnoreCase("export") && !admin) return List.of();
        if (args.length == 4 && !args[1].equalsIgnoreCase("menu")) return List.of("all", "today", "week");
        if (args.length == 3 && (args[1].equalsIgnoreCase("stats") || args[1].equalsIgnoreCase("export") || args[1].equalsIgnoreCase("menu"))) {
            List<String> names = new ArrayList<>();
            if (others) {
                names.add("all");
                Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            }
            if (!args[1].equalsIgnoreCase("menu")) {
                names.add("today");
                names.add("week");
            }
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private Target resolveTarget(CommandSender sender, String requested) {
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        String name = sender instanceof Player player ? player.getName() : "all";
        if (requested != null) {
            if (requested.equalsIgnoreCase("all")) {
                playerId = null;
                name = "all";
            } else {
                Player player = Bukkit.getPlayerExact(requested);
                if (player != null) {
                    playerId = player.getUniqueId();
                    name = player.getName();
                } else {
                    try {
                        playerId = UUID.fromString(requested);
                        name = requested;
                    } catch (IllegalArgumentException ignored) {
                        sender.sendMessage(lang.text(sender, "harvest.stats.player-not-found"));
                        return null;
                    }
                }
            }
        }
        return new Target(playerId, name);
    }

    private void showHarvestStats(CommandSender sender, Target target, HarvestPeriod period) {
        UUID playerId = target.player();
        HarvestStats.Summary summary = stats.summary(playerId, period);
        if (!stats.enabled()) sender.sendMessage(lang.text(sender, "harvest.stats.disabled"));
        if (!stats.persistenceReady()) sender.sendMessage(lang.text(sender, "harvest.stats.persistence-unavailable"));
        sender.sendMessage(lang.text(sender, "harvest.stats.summary")
                .replace("{player}", target.name())
                .replace("{period}", lang.text(sender, "harvest.period." + period.id()))
                .replace("{blocks}", Long.toString(summary.harvests()))
                .replace("{items}", Long.toString(summary.items()))
                .replace("{collected}", Long.toString(summary.collectedItems()))
                .replace("{dropped}", Long.toString(summary.droppedItems()))
                .replace("{quality}", Long.toString(summary.qualityItems()))
                .replace("{bonus}", Long.toString(summary.bonusItems())));
        for (HarvestStats.Breakdown row : stats.breakdown(playerId, 5, period)) {
            sender.sendMessage(lang.text(sender, "harvest.stats.row")
                    .replace("{tool}", row.tool())
                    .replace("{crop}", row.crop())
                    .replace("{blocks}", Long.toString(row.totals().harvests()))
                    .replace("{items}", Long.toString(row.totals().items()))
                    .replace("{quality}", Long.toString(row.totals().qualityItems()))
                    .replace("{bonus}", Long.toString(row.totals().bonusItems())));
        }
        for (HarvestStats.ItemTotal row : stats.items(playerId, 5, period)) {
            sender.sendMessage(lang.text(sender, "harvest.stats.item")
                    .replace("{item}", row.item())
                    .replace("{items}", Long.toString(row.output().items()))
                    .replace("{quality}", Long.toString(row.output().quality()))
                    .replace("{bonus}", Long.toString(row.output().bonus())));
        }
    }

    private record Target(UUID player, String name) { }

    private boolean allowed(CommandSender sender, boolean allowed) {
        if (!allowed) sender.sendMessage(lang.text(sender, "command.no-permission"));
        return allowed;
    }

    @Override
    public String permission() {
        return null;
    }
}
