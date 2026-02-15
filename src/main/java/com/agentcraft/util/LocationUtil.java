package com.agentcraft.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class LocationUtil {

    public static float[] calculateYawPitch(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        return new float[]{yaw, pitch};
    }

    public static Location findSafeGround(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Search downward from location for solid ground
        for (int y = location.getBlockY(); y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (block.getType().isSolid() && !above.getType().isSolid() && !above2.getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, location.getYaw(), location.getPitch());
            }
        }

        return location;
    }

    public static double distanceXZ(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isWithinRange(Location a, Location b, double range) {
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.distanceSquared(b) <= range * range;
    }

    /**
     * Find a safe standing position adjacent to a target block.
     * Checks all 4 cardinal directions for a solid ground block with 2 air blocks above.
     * Returns null if no safe position found.
     */
    public static Location findAdjacentStandingPosition(Location blockLoc) {
        World world = blockLoc.getWorld();
        int bx = blockLoc.getBlockX();
        int by = blockLoc.getBlockY();
        int bz = blockLoc.getBlockZ();

        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int[] off : offsets) {
            int ax = bx + off[0];
            int az = bz + off[1];

            // Check at block Y, Y-1, Y+1 for a walkable spot
            for (int dy = -1; dy <= 1; dy++) {
                int ay = by + dy;
                if (ay < world.getMinHeight() + 1) continue;

                Block ground = world.getBlockAt(ax, ay - 1, az);
                Block feet = world.getBlockAt(ax, ay, az);
                Block head = world.getBlockAt(ax, ay + 1, az);

                if (ground.getType().isSolid() && !feet.getType().isSolid() && !head.getType().isSolid()) {
                    Location candidate = new Location(world, ax + 0.5, ay, az + 0.5);
                    double distSq = candidate.distanceSquared(blockLoc);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                    break; // found a valid Y for this direction
                }
            }
        }

        return best;
    }
}
