package com.agentcraft.listener;

import com.agentcraft.npc.NPCTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final NPCTracker tracker;
    private final org.bukkit.plugin.Plugin plugin;

    public PlayerJoinListener(NPCTracker tracker, org.bukkit.plugin.Plugin plugin) {
        this.tracker = tracker;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay slightly to ensure client is ready
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> tracker.showToPlayer(event.getPlayer()),
                20L
        );
    }
}
