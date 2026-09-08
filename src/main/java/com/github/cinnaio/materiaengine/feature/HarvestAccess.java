package com.github.cinnaio.materiaengine.feature;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class HarvestAccess {
    private HarvestAccess() { }

    public static boolean admin(CommandSender sender) { return sender.hasPermission("materiaengine.admin"); }

    public static boolean others(CommandSender sender) {
        return admin(sender) || sender.hasPermission("materiaengine.harvest.others");
    }

    public static boolean read(CommandSender sender, UUID target) {
        if (sender instanceof Player player && player.getUniqueId().equals(target)) {
            return admin(sender) || sender.hasPermission("materiaengine.harvest");
        }
        return others(sender);
    }
}
