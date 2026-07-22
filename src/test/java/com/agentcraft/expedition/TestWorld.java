package com.agentcraft.expedition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Test fixture: a mocked {@link World} backed by a sparse map of block types.
 * Coordinates that were explicitly set via {@link #setType} get a dedicated
 * {@link Block} mock (with working getType/getLocation/breakNaturally); all
 * other coordinates share a single default block mock.
 */
final class TestWorld {

    final World world = mock(World.class);

    private final Map<String, Material> types = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final Block defaultBlock;
    private Material defaultType = Material.AIR;

    TestWorld() {
        defaultBlock = mock(Block.class);
        when(defaultBlock.getType()).thenAnswer(inv -> defaultType);
        when(defaultBlock.getLocation()).thenReturn(new Location(world, 0, -999, 0));

        when(world.getMinHeight()).thenReturn(-64);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                blockAt(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(world.getBlockAt(any(Location.class))).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            return blockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        });
    }

    void setDefault(Material material) {
        this.defaultType = material;
    }

    void setType(int x, int y, int z, Material material) {
        types.put(key(x, y, z), material);
        blockAt(x, y, z); // ensure dedicated mock exists
    }

    Material getType(int x, int y, int z) {
        return types.getOrDefault(key(x, y, z), defaultType);
    }

    Block blockAt(int x, int y, int z) {
        String k = key(x, y, z);
        if (!types.containsKey(k)) {
            return defaultBlock;
        }
        return blocks.computeIfAbsent(k, kk -> {
            Block b = mock(Block.class);
            when(b.getType()).thenAnswer(inv -> getType(x, y, z));
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            doAnswer(inv -> {
                types.put(k, Material.AIR);
                return true;
            }).when(b).breakNaturally();
            return b;
        });
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
