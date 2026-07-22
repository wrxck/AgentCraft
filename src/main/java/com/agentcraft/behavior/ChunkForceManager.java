package com.agentcraft.behavior;

import com.agentcraft.agent.AIAgent;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.logging.Logger;

/**
 * Force-loads chunks around NPCs so they remain active even when no players are nearby.
 */
public class ChunkForceManager {

    private final Logger logger;
    private final boolean enabled;
    private final int radius;

    /** The chunks one agent is keeping loaded, all in a single world. */
    private record AgentHold(World world, Set<Long> chunks) {}

    // Track which chunks each agent is keeping loaded: agentName -> hold
    private final Map<String, AgentHold> agentChunks = new HashMap<>();

    // Reference count per world and packed chunk coords, so overlapping agents
    // don't unforce each other's chunks and same-coordinate chunks in
    // different worlds don't share a refcount entry.
    private final Map<World, Map<Long, Integer>> chunkRefCounts = new HashMap<>();

    public ChunkForceManager(Logger logger, boolean enabled, int radius) {
        this.logger = logger;
        this.enabled = enabled;
        this.radius = radius;
    }

    /**
     * Update forced chunks for an agent. Call on spawn/restore and when NPC moves >16 blocks.
     */
    public void updateForced(AIAgent agent) {
        if (!enabled) return;

        String name = agent.getNpc().getName().toLowerCase();
        Location loc = agent.getNpc().getLocation();
        World world = loc.getWorld();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        // Calculate new chunk set
        Set<Long> newChunks = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                newChunks.add(packChunk(chunkX + dx, chunkZ + dz));
            }
        }

        AgentHold oldHold = agentChunks.get(name);
        if (oldHold != null && !oldHold.world().equals(world)) {
            // World changed: release everything held in the old world first.
            for (long packed : oldHold.chunks()) {
                releaseChunk(oldHold.world(), packed);
            }
            oldHold = null;
        }

        Set<Long> oldChunks = oldHold != null ? oldHold.chunks() : Collections.emptySet();

        // Release old chunks that are no longer needed
        for (long packed : oldChunks) {
            if (!newChunks.contains(packed)) {
                releaseChunk(world, packed);
            }
        }

        // Force new chunks
        for (long packed : newChunks) {
            if (!oldChunks.contains(packed)) {
                forceChunk(world, packed);
            }
        }

        agentChunks.put(name, new AgentHold(world, newChunks));
    }

    /**
     * Release all forced chunks for an agent (on despawn).
     * Each held ticket is released exactly once, against the world it was
     * acquired in.
     */
    public void releaseAll(String agentName) {
        if (!enabled) return;

        String name = agentName.toLowerCase();
        AgentHold hold = agentChunks.remove(name);
        if (hold == null) return;

        for (long packed : hold.chunks()) {
            releaseChunk(hold.world(), packed);
        }
    }

    /**
     * Release all chunks for all agents (shutdown).
     */
    public void shutdown() {
        if (!enabled) return;

        for (Map.Entry<World, Map<Long, Integer>> worldEntry : new HashMap<>(chunkRefCounts).entrySet()) {
            World world = worldEntry.getKey();
            for (long packed : new ArrayList<>(worldEntry.getValue().keySet())) {
                int cx = unpackX(packed);
                int cz = unpackZ(packed);
                if (world.isChunkForceLoaded(cx, cz)) {
                    world.setChunkForceLoaded(cx, cz, false);
                }
            }
        }
        agentChunks.clear();
        chunkRefCounts.clear();
    }

    private void forceChunk(World world, long packed) {
        Map<Long, Integer> refs = chunkRefCounts.computeIfAbsent(world, w -> new HashMap<>());
        int count = refs.getOrDefault(packed, 0);
        if (count == 0) {
            world.setChunkForceLoaded(unpackX(packed), unpackZ(packed), true);
        }
        refs.put(packed, count + 1);
    }

    private void releaseChunk(World world, long packed) {
        Map<Long, Integer> refs = chunkRefCounts.get(world);
        int count = refs != null ? refs.getOrDefault(packed, 0) : 0;
        if (count <= 1) {
            if (refs != null) {
                refs.remove(packed);
                if (refs.isEmpty()) {
                    chunkRefCounts.remove(world);
                }
            }
            world.setChunkForceLoaded(unpackX(packed), unpackZ(packed), false);
        } else {
            refs.put(packed, count - 1);
        }
    }

    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
