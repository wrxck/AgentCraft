package com.agentcraft.listener;

import com.agentcraft.npc.NPCTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final NPCTracker tracker;

    public PlayerJoinListener(NPCTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay slightly to ensure client is ready
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("AgentCraft"),
                () -> tracker.showToPlayer(event.getPlayer()),
                20L
        );
    }
}
