package com.agentcraft.navigation;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Small grid-world harness: a mocked {@link World} backed by a map of
 * (x, y, z) -> Material, defaulting to AIR. Columns are made walkable by
 * placing a solid ground block one below the desired feet level.
 */
public final class GridWorld {

    private final Map<Long, Material> materials = new HashMap<>();
    private final Map<Long, Block> blockCache = new HashMap<>();
    public final World world;

    public GridWorld() {
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            return blockAt(x, y, z);
        });
    }

    private Block blockAt(int x, int y, int z) {
        long key = key(x, y, z);
        return blockCache.computeIfAbsent(key, k -> {
            Material mat = materials.getOrDefault(key, Material.AIR);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(mat);
            return block;
        });
    }

    public void set(int x, int y, int z, Material material) {
        materials.put(key(x, y, z), material);
        blockCache.remove(key(x, y, z));
    }

    public Material get(int x, int y, int z) {
        return materials.getOrDefault(key(x, y, z), Material.AIR);
    }

    /** Makes (x, feetY, z) a standable position: solid ground at feetY - 1. */
    public void column(int x, int z, int feetY) {
        set(x, feetY - 1, z, Material.STONE);
    }

    /** Fills a rectangle of standable columns, all at the same feet level. */
    public void plane(int minX, int maxX, int minZ, int maxZ, int feetY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                column(x, z, feetY);
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }
}
