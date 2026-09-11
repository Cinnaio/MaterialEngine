package com.github.cinnaio.teastory.feature;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class HarvestAccess {
    private HarvestAccess() { }

    public static boolean admin(CommandSender sender) {
        return has(sender, "teastory.admin", "materiaengine.admin");
    }

    public static boolean others(CommandSender sender) {
        return admin(sender) || has(sender, "teastory.harvest.others", "materiaengine.harvest.others");
    }

    public static boolean read(CommandSender sender, UUID target) {
        if (sender instanceof Player player && player.getUniqueId().equals(target)) {
            return admin(sender) || has(sender, "teastory.harvest", "materiaengine.harvest");
        }
        return others(sender);
    }

    private static boolean has(CommandSender sender, String current, String legacy) {
        return sender.hasPermission(current) || sender.hasPermission(legacy);
    }
}
