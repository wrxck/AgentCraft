package com.agentcraft.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationUtilTest {

    private World world;
    private final Map<String, Material> blocks = new HashMap<>();

    @BeforeEach
    void setUp() {
        blocks.clear();
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            Material mat = blocks.getOrDefault(x + "," + y + "," + z, Material.AIR);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(mat);
            return block;
        });
    }

    private void set(int x, int y, int z, Material mat) {
        blocks.put(x + "," + y + "," + z, mat);
    }

    @Test
    void calculateYawPitchKnownAngles() {
        Location from = new Location(world, 0, 64, 0);

        // Due south (+Z) is yaw 0.
        float[] south = LocationUtil.calculateYawPitch(from, new Location(world, 0, 64, 10));
        assertEquals(0.0f, south[0], 1e-4);
        assertEquals(0.0f, south[1], 1e-4);

        // Due west (-X) is yaw 90.
        float[] west = LocationUtil.calculateYawPitch(from, new Location(world, -10, 64, 0));
        assertEquals(90.0f, west[0], 1e-4);

        // Due east (+X) is yaw -90.
        float[] east = LocationUtil.calculateYawPitch(from, new Location(world, 10, 64, 0));
        assertEquals(-90.0f, east[0], 1e-4);

        // Straight up is pitch -90.
        float[] up = LocationUtil.calculateYawPitch(from, new Location(world, 0, 74, 0));
        assertEquals(-90.0f, up[1], 1e-4);

        // Straight down is pitch 90.
        float[] down = LocationUtil.calculateYawPitch(from, new Location(world, 0, 54, 0));
        assertEquals(90.0f, down[1], 1e-4);

        // 45 degrees down over equal horizontal and vertical distance.
        float[] diag = LocationUtil.calculateYawPitch(from, new Location(world, 0, 54, 10));
        assertEquals(45.0f, diag[1], 1e-4);
    }

    @Test
    void distanceXZIgnoresY() {
        Location a = new Location(world, 0, 0, 0);
        Location b = new Location(world, 3, 250, 4);
        assertEquals(5.0, LocationUtil.distanceXZ(a, b), 1e-9);
    }

    @Test
    void findSafeGroundFindsFirstSolidBelow() {
        set(0, 60, 0, Material.STONE); // ground; standing spot is y=61
        Location result = LocationUtil.findSafeGround(new Location(world, 0.2, 70, 0.7));
        assertEquals(61, result.getBlockY());
        assertEquals(0.5, result.getX(), 1e-9);
        assertEquals(0.5, result.getZ(), 1e-9);
    }

    @Test
    void findSafeGroundFallsBackToOriginalWhenNoGround() {
        Location original = new Location(world, 5.5, 70, 5.5);
        Location result = LocationUtil.findSafeGround(original);
        assertSame(original, result, "with no solid ground the original location is returned");
    }

    @Test
    void findAdjacentStandingPositionFindsCardinalSpot() {
        // Target block at (0, 64, 0); solid ground at (1, 63, 0) so feet y=64 works.
        set(1, 63, 0, Material.STONE);
        Location result = LocationUtil.findAdjacentStandingPosition(new Location(world, 0, 64, 0));
        assertNotNull(result);
        assertEquals(1, result.getBlockX());
        assertEquals(64, result.getBlockY());
        assertEquals(0, result.getBlockZ());
    }

    @Test
    void findAdjacentStandingPositionReturnsNullWhenNoneExists() {
        assertNull(LocationUtil.findAdjacentStandingPosition(new Location(world, 0, 64, 0)));
    }

    @Test
    void findAdjacentStandingPositionRespectsMinHeightBoundary() {
        // Block at the very bottom of the world: candidate Y levels below
        // minHeight + 1 must be skipped without blowing up.
        set(1, 0, 0, Material.STONE); // ground at y=0 -> feet y=1 is the only legal spot
        Location result = LocationUtil.findAdjacentStandingPosition(new Location(world, 0, 0, 0));
        assertNotNull(result);
        assertEquals(1, result.getBlockY());
    }
}
