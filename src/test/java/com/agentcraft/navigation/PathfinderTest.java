package com.agentcraft.navigation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PathfinderTest {

    private static final double SQRT2 = Math.sqrt(2);
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static Pathfinder.Status runToCompletion(Pathfinder pathfinder) {
        Pathfinder.Status status = pathfinder.step();
        int safety = 0;
        while (status == Pathfinder.Status.IN_PROGRESS && safety++ < 1000) {
            status = pathfinder.step();
        }
        return status;
    }

    /** Total path cost using the same cost model as Pathfinder.step(). */
    private static double pathCost(GridWorld g, List<PathNode> path) {
        double cost = 0;
        for (int i = 1; i < path.size(); i++) {
            PathNode prev = path.get(i - 1);
            PathNode cur = path.get(i);
            cost += moveCost(g, prev, cur);
        }
        return cost;
    }

    private static double moveCost(GridWorld g, PathNode from, PathNode to) {
        boolean diagonal = from.x != to.x && from.z != to.z;
        double cost = diagonal ? SQRT2 : 1.0;
        if (g.get(to.x, to.y, to.z) == Material.WATER) cost *= 2.0;
        int stepUp = to.y - from.y;
        int stepDown = from.y - to.y;
        if (stepUp > 0) cost += 0.5;
        if (stepDown > 0) cost += 0.3 * stepDown;
        return cost;
    }

    // --- Brute-force Dijkstra ground truth over the same movement rules ---

    private record State(int x, int y, int z) {}

    private static int findWalkableY(GridWorld g, int x, int baseY, int z) {
        for (int y = baseY + 1; y >= baseY - 3; y--) {
            if (y < -64 + 1 || y > 320 - 2) continue;
            if (!g.get(x, y - 1, z).isSolid()) continue;
            if (g.get(x, y, z).isSolid()) continue;
            if (g.get(x, y + 1, z).isSolid()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Dijkstra using exactly the neighbor generation and costs of Pathfinder.
     * Returns the optimal cost of reaching the goal acceptance region
     * (|dx| <= 1, |dz| <= 1, |dy| <= 2), or NaN if unreachable.
     */
    private static double dijkstraCost(GridWorld g, State start, State goal) {
        Map<State, Double> dist = new HashMap<>();
        PriorityQueue<double[]> queue = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        Map<Integer, State> ids = new HashMap<>();
        int nextId = 0;
        dist.put(start, 0.0);
        ids.put(nextId, start);
        queue.add(new double[]{0.0, nextId++});
        Set<State> settled = new HashSet<>();

        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            State current = ids.get((int) top[1]);
            if (!settled.add(current)) continue;
            double d = dist.get(current);

            if (Math.abs(current.x() - goal.x()) <= 1
                    && Math.abs(current.z() - goal.z()) <= 1
                    && Math.abs(current.y() - goal.y()) <= 2) {
                return d;
            }
            if (d > 50) continue;

            for (int[] dir : DIRECTIONS) {
                int nx = current.x() + dir[0];
                int nz = current.z() + dir[1];
                boolean diagonal = dir[0] != 0 && dir[1] != 0;
                if (diagonal) {
                    if (findWalkableY(g, current.x() + dir[0], current.y(), current.z()) == Integer.MIN_VALUE) continue;
                    if (findWalkableY(g, current.x(), current.y(), current.z() + dir[1]) == Integer.MIN_VALUE) continue;
                }
                int ny = findWalkableY(g, nx, current.y(), nz);
                if (ny == Integer.MIN_VALUE) continue;
                if (ny - current.y() > 1) continue;
                if (current.y() - ny > 3) continue;

                State neighbor = new State(nx, ny, nz);
                double cost = moveCost(g, new PathNode(current.x(), current.y(), current.z()),
                        new PathNode(nx, ny, nz));
                double nd = d + cost;
                if (nd < dist.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    dist.put(neighbor, nd);
                    ids.put(nextId, neighbor);
                    queue.add(new double[]{nd, nextId++});
                }
            }
        }
        return Double.NaN;
    }

    // --- Tests ---

    @Test
    void flatStraightLinePathIsFound() {
        GridWorld g = new GridWorld();
        g.plane(0, 6, 0, 0, 65);

        Pathfinder pf = new Pathfinder(g.world,
                new Location(g.world, 0.5, 65, 0.5),
                new Location(g.world, 6.5, 65, 0.5));
        assertEquals(Pathfinder.Status.FOUND, runToCompletion(pf));

        List<PathNode> path = pf.getResult();
        assertNotNull(path);
        assertEquals(0, path.get(0).x);
        // Straight flat corridor: cost equals horizontal distance to the accepted node.
        double cost = pathCost(g, path);
        double optimal = dijkstraCost(g, new State(0, 65, 0), new State(6, 65, 0));
        assertEquals(optimal, cost, 1e-9);
        for (PathNode node : path) {
            assertEquals(65, node.y);
            assertEquals(0, node.z);
        }
    }

    @Test
    void downhillSlopePathCostMatchesDijkstraOptimum() {
        GridWorld g = new GridWorld();
        // Two lanes separated by an unwalkable wall row at z=1 (gaps at x=0 and
        // x=14). Start is in the low-road lane; the goal is 4 blocks below the
        // start level at the end of the high-road lane.
        //
        // High road (z=0, optimal): enter via the x=0 gap, stay flat at y=65
        // until x=12, then drop down a 3-block cliff right before the goal.
        // Low road (z=2, longer): descend early via four 1-block steps, run the
        // whole way at y=61, and re-enter through the x=14 gap.
        //
        // A heuristic that charges 1.0 per block of |dy| (instead of the real
        // 0.3/block bundled descent cost) inflates f on the high road, so A*
        // commits to the longer low road and returns a suboptimal path.
        g.column(0, 1, 65);   // start column (wall gap)
        g.column(14, 1, 61);  // far wall gap

        for (int x = 0; x <= 12; x++) g.column(x, 0, 65); // high road
        g.column(13, 0, 62);                              // cliff base
        g.column(14, 0, 61);                              // goal column

        g.column(0, 2, 65);                               // low road
        g.column(1, 2, 64);
        g.column(2, 2, 63);
        g.column(3, 2, 62);
        for (int x = 4; x <= 14; x++) g.column(x, 2, 61);

        Location start = new Location(g.world, 0.5, 65, 2.5);
        Location goal = new Location(g.world, 14.5, 61, 0.5);

        Pathfinder pf = new Pathfinder(g.world, start, goal);
        assertEquals(Pathfinder.Status.FOUND, runToCompletion(pf));

        double cost = pathCost(g, pf.getResult());
        double optimal = dijkstraCost(g, new State(0, 65, 2), new State(14, 61, 0));
        assertFalse(Double.isNaN(optimal));
        // An admissible heuristic must produce the optimal-cost path.
        assertEquals(optimal, cost, 1e-9);
    }

    @Test
    void singleStepUpIsTraversable() {
        GridWorld g = new GridWorld();
        g.column(0, 0, 65);
        g.column(1, 0, 66);
        g.column(2, 0, 67);
        g.column(3, 0, 67);
        g.column(4, 0, 67);

        Pathfinder pf = new Pathfinder(g.world,
                new Location(g.world, 0.5, 65, 0.5),
                new Location(g.world, 4.5, 67, 0.5));
        assertEquals(Pathfinder.Status.FOUND, runToCompletion(pf));
    }

    @Test
    void twoBlockStepUpIsNotTraversable() {
        GridWorld g = new GridWorld();
        g.column(0, 0, 65);
        g.column(1, 0, 65);
        // 2-block step up at x=2 (feet would be 67); MAX_STEP_UP is 1.
        g.column(2, 0, 67);
        g.set(2, 65, 0, Material.STONE); // wall body
        g.set(2, 66, 0, Material.STONE);
        g.column(3, 0, 67);
        g.column(4, 0, 67);

        Pathfinder pf = new Pathfinder(g.world,
                new Location(g.world, 0.5, 65, 0.5),
                new Location(g.world, 4.5, 67, 0.5));
        assertEquals(Pathfinder.Status.NOT_FOUND, runToCompletion(pf));
    }

    @Test
    void unreachableGoalReturnsNotFound() {
        GridWorld g = new GridWorld();
        g.plane(0, 3, 0, 3, 65);
        // Goal far outside the walkable island.
        Pathfinder pf = new Pathfinder(g.world,
                new Location(g.world, 0.5, 65, 0.5),
                new Location(g.world, 40.5, 65, 40.5));
        assertEquals(Pathfinder.Status.NOT_FOUND, runToCompletion(pf));
    }

    @Test
    void iterationBudgetResumesAcrossTicks() {
        GridWorld g = new GridWorld();
        // 20x20 walkable island, goal unreachable: the search must exhaust
        // all ~400 nodes, which exceeds the 200 iterations-per-tick budget.
        g.plane(0, 19, 0, 19, 65);
        Pathfinder pf = new Pathfinder(g.world,
                new Location(g.world, 0.5, 65, 0.5),
                new Location(g.world, 100.5, 65, 100.5));

        Pathfinder.Status first = pf.step();
        assertEquals(Pathfinder.Status.IN_PROGRESS, first, "first tick should hit the iteration budget");

        Pathfinder.Status status = first;
        int ticks = 1;
        while (status == Pathfinder.Status.IN_PROGRESS && ticks++ < 100) {
            status = pf.step();
        }
        assertEquals(Pathfinder.Status.NOT_FOUND, status);
        assertTrue(pf.getTotalIterations() > 200, "search should resume across ticks");
    }
}
