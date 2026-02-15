package com.agentcraft.listener;

import com.agentcraft.npc.NPCTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final NPCTracker tracker;

    public PlayerQuitListener(NPCTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tracker.hideFromPlayer(event.getPlayer());
    }
}
