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
        // Delay slightly to ensure client is ready. Guard against players who
        // disconnect again before the delayed task runs.
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (event.getPlayer().isOnline()) {
                        tracker.showToPlayer(event.getPlayer());
                    }
                },
                20L
        );
    }
}
