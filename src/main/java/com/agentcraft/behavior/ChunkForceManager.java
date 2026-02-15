package com.agentcraft.behavior;

import com.agentcraft.agent.AIAgent;
import org.bukkit.Chunk;
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

    // Track which chunks each agent is keeping loaded: agentName -> set of packed chunk coords
    private final Map<String, Set<Long>> agentChunks = new HashMap<>();

    // Reference count per chunk so overlapping agents don't unforce each other's chunks
    private final Map<Long, Integer> chunkRefCounts = new HashMap<>();

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
                newChunks.add(packChunk(world, chunkX + dx, chunkZ + dz));
            }
        }

        // Release old chunks that are no longer needed
        Set<Long> oldChunks = agentChunks.getOrDefault(name, Collections.emptySet());
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

        agentChunks.put(name, newChunks);
    }

    /**
     * Release all forced chunks for an agent (on despawn).
     */
    public void releaseAll(String agentName) {
        if (!enabled) return;

        String name = agentName.toLowerCase();
        Set<Long> chunks = agentChunks.remove(name);
        if (chunks == null) return;

        // We need the world — find it from any remaining agent or just iterate all worlds
        for (long packed : chunks) {
            for (World world : org.bukkit.Bukkit.getWorlds()) {
                int cx = unpackX(packed);
                int cz = unpackZ(packed);
                releaseChunk(world, packed);
            }
        }
    }

    /**
     * Release all chunks for all agents (shutdown).
     */
    public void shutdown() {
        if (!enabled) return;

        for (Map.Entry<Long, Integer> entry : new HashMap<>(chunkRefCounts).entrySet()) {
            long packed = entry.getKey();
            int cx = unpackX(packed);
            int cz = unpackZ(packed);
            for (World world : org.bukkit.Bukkit.getWorlds()) {
                if (world.isChunkForceLoaded(cx, cz)) {
                    world.setChunkForceLoaded(cx, cz, false);
                }
            }
        }
        agentChunks.clear();
        chunkRefCounts.clear();
    }

    private void forceChunk(World world, long packed) {
        int cx = unpackX(packed);
        int cz = unpackZ(packed);
        int refs = chunkRefCounts.getOrDefault(packed, 0);
        if (refs == 0) {
            world.setChunkForceLoaded(cx, cz, true);
        }
        chunkRefCounts.put(packed, refs + 1);
    }

    private void releaseChunk(World world, long packed) {
        int refs = chunkRefCounts.getOrDefault(packed, 0);
        if (refs <= 1) {
            chunkRefCounts.remove(packed);
            int cx = unpackX(packed);
            int cz = unpackZ(packed);
            world.setChunkForceLoaded(cx, cz, false);
        } else {
            chunkRefCounts.put(packed, refs - 1);
        }
    }

    private static long packChunk(World world, int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
