package com.agentcraft.npc;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NPCTracker {

    private final Map<Integer, FakePlayer> byEntityId = new ConcurrentHashMap<>();
    private final Map<String, FakePlayer> byName = new ConcurrentHashMap<>();

    public void register(FakePlayer npc) {
        byEntityId.put(npc.getEntityId(), npc);
        byName.put(npc.getName().toLowerCase(), npc);
    }

    public void unregister(FakePlayer npc) {
        byEntityId.remove(npc.getEntityId());
        byName.remove(npc.getName().toLowerCase());
    }

    public FakePlayer getByEntityId(int entityId) {
        return byEntityId.get(entityId);
    }

    public FakePlayer getByName(String name) {
        return byName.get(name.toLowerCase());
    }

    public Collection<FakePlayer> getAll() {
        return byEntityId.values();
    }

    public boolean isNPC(int entityId) {
        return byEntityId.containsKey(entityId);
    }

    public boolean hasName(String name) {
        return byName.containsKey(name.toLowerCase());
    }

    public void showToPlayer(Player player) {
        for (FakePlayer npc : new ArrayList<>(byEntityId.values())) {
            if (isInViewDistance(player, npc.getLocation())) {
                npc.spawn(player);
            }
        }
    }

    public void hideFromPlayer(Player player) {
        for (FakePlayer npc : new ArrayList<>(byEntityId.values())) {
            npc.despawn(player);
        }
    }

    public void showNPCsInChunk(Player player, Chunk chunk) {
        for (FakePlayer npc : new ArrayList<>(byEntityId.values())) {
            if (isInChunk(npc.getLocation(), chunk)) {
                npc.spawn(player);
            }
        }
    }

    /**
     * True if the location lies inside the given chunk (same world and chunk
     * coordinates). Shared helper for the "NPC in chunk" test; ChunkListener
     * duplicates this logic and can delegate here as well.
     */
    public static boolean isInChunk(Location loc, Chunk chunk) {
        return loc.getWorld().equals(chunk.getWorld())
                && loc.getBlockX() >> 4 == chunk.getX()
                && loc.getBlockZ() >> 4 == chunk.getZ();
    }

    public void despawnAll() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            hideFromPlayer(player);
        }
        byEntityId.clear();
        byName.clear();
    }

    private boolean isInViewDistance(Player player, Location npcLoc) {
        if (!player.getWorld().equals(npcLoc.getWorld())) return false;
        int viewDist = player.getClientViewDistance();
        double maxDist = viewDist * 16.0;
        return player.getLocation().distanceSquared(npcLoc) <= maxDist * maxDist;
    }

    public int count() {
        return byEntityId.size();
    }
}
