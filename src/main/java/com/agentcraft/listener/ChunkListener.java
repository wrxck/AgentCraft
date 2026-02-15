package com.agentcraft.listener;

import com.agentcraft.npc.NPCTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkListener implements Listener {

    private final NPCTracker tracker;

    public ChunkListener(NPCTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (org.bukkit.entity.Player player : event.getWorld().getPlayers()) {
            tracker.showNPCsInChunk(player, event.getChunk());
        }
    }
}
