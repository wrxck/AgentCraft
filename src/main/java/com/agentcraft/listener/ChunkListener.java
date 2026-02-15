package com.agentcraft.listener;

import com.agentcraft.npc.FakePlayer;
import com.agentcraft.npc.NPCTracker;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

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

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (org.bukkit.entity.Player player : event.getWorld().getPlayers()) {
            for (FakePlayer npc : tracker.getAll()) {
                Location loc = npc.getLocation();
                if (loc.getWorld().equals(event.getChunk().getWorld())
                        && loc.getBlockX() >> 4 == event.getChunk().getX()
                        && loc.getBlockZ() >> 4 == event.getChunk().getZ()) {
                    npc.despawn(player);
                }
            }
        }
    }
}
