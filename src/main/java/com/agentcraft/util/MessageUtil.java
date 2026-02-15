package com.agentcraft.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class MessageUtil {

    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.AQUA + "AgentCraft" + ChatColor.GRAY + "] ";

    public static void send(Player player, String message) {
        player.sendMessage(PREFIX + message);
    }

    public static void sendAll(Iterable<? extends Player> players, String message) {
        String formatted = PREFIX + message;
        for (Player player : players) {
            player.sendMessage(formatted);
        }
    }

    public static String info(String message) {
        return ChatColor.WHITE + message;
    }

    public static String success(String message) {
        return ChatColor.GREEN + message;
    }

    public static String error(String message) {
        return ChatColor.RED + message;
    }

    public static String warning(String message) {
        return ChatColor.YELLOW + message;
    }

    public static String highlight(String text) {
        return ChatColor.AQUA + text + ChatColor.WHITE;
    }

    public static String agentChat(String agentName, String message) {
        return ChatColor.GRAY + "[" + ChatColor.GREEN + agentName + ChatColor.GRAY + "] " + ChatColor.WHITE + message;
    }
}
