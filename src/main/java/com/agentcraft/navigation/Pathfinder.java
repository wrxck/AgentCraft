package com.agentcraft.navigation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

public class Pathfinder {

    public enum Status { IN_PROGRESS, FOUND, NOT_FOUND }

    private static final int MAX_ITERATIONS_PER_TICK = 200;
    private static final int MAX_PATH_LENGTH = 50;
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_STEP_DOWN = 3;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},           // cardinal
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}           // diagonal
    };
    private static final double SQRT2 = Math.sqrt(2);

    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.CACTUS, Material.MAGMA_BLOCK, Material.POWDER_SNOW,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE
    );

    private final World world;
    private final PathNode startNode;
    private final PathNode goalNode;

    private final PriorityQueue<PathNode> openSet = new PriorityQueue<>();
    private final Map<Long, PathNode> openMap = new HashMap<>();
    private final Set<Long> closedSet = new HashSet<>();

    private Status status = Status.IN_PROGRESS;
    private List<PathNode> result;
    private int totalIterations;

    public Pathfinder(World world, Location start, Location goal) {
        this.world = world;
        this.startNode = new PathNode(start.getBlockX(), start.getBlockY(), start.getBlockZ());
        this.goalNode = new PathNode(goal.getBlockX(), goal.getBlockY(), goal.getBlockZ());

        startNode.g = 0;
        startNode.h = heuristic(startNode, goalNode);
        startNode.f = startNode.h;

        openSet.add(startNode);
        openMap.put(startNode.packedPos(), startNode);
    }

    /**
     * Run one batch of iterations. Call once per tick.
     * Returns current status: IN_PROGRESS, FOUND, or NOT_FOUND.
     */
    public Status step() {
        if (status != Status.IN_PROGRESS) return status;

        int iterations = 0;
        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS_PER_TICK) {
            iterations++;
            totalIterations++;

            PathNode current = openSet.poll();
            long currentPacked = current.packedPos();
            openMap.remove(currentPacked);

            // Goal reached (within 1 block horizontally, any Y within 2)
            if (Math.abs(current.x - goalNode.x) <= 1
                    && Math.abs(current.z - goalNode.z) <= 1
                    && Math.abs(current.y - goalNode.y) <= 2) {
                result = reconstructPath(current);
                status = Status.FOUND;
                return status;
            }

            closedSet.add(currentPacked);

            // Path too long
            if (current.g > MAX_PATH_LENGTH) continue;

            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                boolean diagonal = dir[0] != 0 && dir[1] != 0;

                // Chunk guard - skip unloaded chunks
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                // Corner-cut prevention: for diagonal moves, both adjacent cardinals must be passable
                if (diagonal) {
                    if (findWalkableY(current.x + dir[0], current.y, current.z) == Integer.MIN_VALUE) continue;
                    if (findWalkableY(current.x, current.y, current.z + dir[1]) == Integer.MIN_VALUE) continue;
                }

                // Find walkable Y at (nx, nz) relative to current.y
                int ny = findWalkableY(nx, current.y, nz);
                if (ny == Integer.MIN_VALUE) continue;

                // Step constraints
                int stepUp = ny - current.y;
                int stepDown = current.y - ny;
                if (stepUp > MAX_STEP_UP) continue;
                if (stepDown > MAX_STEP_DOWN) continue;

                PathNode neighbor = new PathNode(nx, ny, nz);
                long neighborPacked = neighbor.packedPos();

                if (closedSet.contains(neighborPacked)) continue;

                double moveCost = diagonal ? SQRT2 : 1.0;
                // Water is walkable but costs more
                Material feetMat = world.getBlockAt(nx, ny, nz).getType();
                if (feetMat == Material.WATER) moveCost *= 2.0;
                // Vertical movement costs a bit more
                if (stepUp > 0) moveCost += 0.5;
                if (stepDown > 0) moveCost += 0.3 * stepDown;

                double tentativeG = current.g + moveCost;

                PathNode existing = openMap.get(neighborPacked);
                if (existing != null && tentativeG >= existing.g) continue;

                neighbor.g = tentativeG;
                neighbor.h = heuristic(neighbor, goalNode);
                neighbor.f = neighbor.g + neighbor.h;
                neighbor.parent = current;

                if (existing != null) {
                    openSet.remove(existing);
                }
                openSet.add(neighbor);
                openMap.put(neighborPacked, neighbor);
            }
        }

        if (openSet.isEmpty()) {
            status = Status.NOT_FOUND;
        }

        return status;
    }

    /**
     * Find a walkable Y at (x, z) near baseY.
     * Ground must be solid, feet and head passable, no hazards.
     * Returns Integer.MIN_VALUE if no walkable position found.
     */
    private int findWalkableY(int x, int baseY, int z) {
        // Search from baseY + MAX_STEP_UP down to baseY - MAX_STEP_DOWN
        int top = baseY + MAX_STEP_UP;
        int bottom = baseY - MAX_STEP_DOWN;

        for (int y = top; y >= bottom; y--) {
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) continue;

            Block ground = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (!ground.getType().isSolid()) continue;
            if (feet.getType().isSolid()) continue;
            if (head.getType().isSolid()) continue;

            // Check hazards at ground, feet, head
            if (HAZARDS.contains(ground.getType())) continue;
            if (HAZARDS.contains(feet.getType())) continue;
            if (HAZARDS.contains(head.getType())) continue;

            return y;
        }

        return Integer.MIN_VALUE;
    }

    private static double heuristic(PathNode a, PathNode b) {
        // Octile distance - admissible for 8-directional movement
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        int dz = Math.abs(a.z - b.z);
        int dMin = Math.min(dx, dz);
        int dMax = Math.max(dx, dz);
        return (SQRT2 - 1) * dMin + dMax + dy;
    }

    private static List<PathNode> reconstructPath(PathNode end) {
        List<PathNode> path = new ArrayList<>();
        PathNode current = end;
        while (current != null) {
            path.add(current);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    public Status getStatus() { return status; }
    public List<PathNode> getResult() { return result; }
    public int getTotalIterations() { return totalIterations; }
}
