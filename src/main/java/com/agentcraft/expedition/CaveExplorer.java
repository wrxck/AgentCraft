package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.util.LocationUtil;
import com.agentcraft.util.MaterialMatcher;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.function.UnaryOperator;

/**
 * Explores caves found during staircase mining, scanning for target materials.
 * Uses NavigationController for movement within the cave.
 */
public class CaveExplorer {

    private static final int SCAN_RADIUS = 8;
    private static final int MAX_EXPLORE_DISTANCE = 100;
    private static final int SEGMENT_LENGTH = 40;

    public enum State { IDLE, SCANNING, NAVIGATING, FOUND_TARGET, EXHAUSTED }

    private final AIAgent agent;
    private final String targetMaterial;
    private final Location entryPoint;

    private State state = State.IDLE;
    private Location targetLocation;
    private double distanceFromEntry;
    private boolean segmentArrived;
    private boolean segmentFailed;

    public CaveExplorer(AIAgent agent, String targetMaterial, Location entryPoint) {
        this.agent = agent;
        this.targetMaterial = targetMaterial.toUpperCase().replace(' ', '_');
        this.entryPoint = entryPoint.clone();
    }

    public void start() {
        state = State.SCANNING;
    }

    public void tick() {
        if (state == State.IDLE || state == State.FOUND_TARGET || state == State.EXHAUSTED) return;

        Location npcLoc = agent.getNpc().getLocation();
        distanceFromEntry = npcLoc.distance(entryPoint);

        // Check if we've gone too far
        if (distanceFromEntry > MAX_EXPLORE_DISTANCE) {
            agent.getBehaviorController().getNavigation().cancel();
            state = State.EXHAUSTED;
            return;
        }

        switch (state) {
            case SCANNING -> tickScanning(npcLoc);
            case NAVIGATING -> tickNavigating(npcLoc);
            default -> {}
        }
    }

    private void tickScanning(Location npcLoc) {
        // Scan for target material
        Location found = scanForMaterial(npcLoc);
        if (found != null) {
            targetLocation = found;
            state = State.FOUND_TARGET;
            return;
        }

        // Find next cave passage to explore
        Location nextPassage = findNextPassage(npcLoc);
        if (nextPassage == null) {
            state = State.EXHAUSTED;
            return;
        }

        // Navigate to it
        segmentArrived = false;
        segmentFailed = false;
        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(nextPassage, () -> segmentArrived = true, () -> segmentFailed = true);
        state = State.NAVIGATING;
    }

    private void tickNavigating(Location npcLoc) {
        // Check for material while navigating
        Location found = scanForMaterial(npcLoc);
        if (found != null) {
            targetLocation = found;
            agent.getBehaviorController().getNavigation().cancel();
            state = State.FOUND_TARGET;
            return;
        }

        if (segmentArrived || segmentFailed
                || !agent.getBehaviorController().getNavigation().isNavigating()) {
            state = State.SCANNING;
        }
    }

    Location scanForMaterial(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Location nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (MaterialMatcher.matches(b.getType(), targetMaterial)) {
                        double distSq = b.getLocation().distanceSquared(center);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = b.getLocation();
                        }
                    }
                }
            }
        }

        return nearest;
    }

    private Location findNextPassage(Location npcLoc) {
        return selectPassage(npcLoc.getWorld(),
                npcLoc.getBlockX(), npcLoc.getBlockY(), npcLoc.getBlockZ(),
                distanceFromEntry, LocationUtil::findSafeGround);
    }

    /**
     * Find the largest air opening in cardinal directions and pick a
     * navigation target in that direction. Package-private and side-effect
     * free so the direction-selection logic can be unit tested.
     */
    static Location selectPassage(World world, int cx, int cy, int cz,
                                  double distanceFromEntry, UnaryOperator<Location> groundFinder) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Location bestPassage = null;
        int bestAirCount = 0;

        for (int[] dir : directions) {
            int airCount = 0;
            // Check 5 blocks out in this direction
            for (int d = 1; d <= 5; d++) {
                int px = cx + dir[0] * d;
                int pz = cz + dir[1] * d;
                for (int dy = -1; dy <= 2; dy++) {
                    Block b = world.getBlockAt(px, cy + dy, pz);
                    if (!b.getType().isSolid()) airCount++;
                }
            }

            if (airCount <= bestAirCount) continue;

            int targetDist = Math.min(SEGMENT_LENGTH, (int) (MAX_EXPLORE_DISTANCE - distanceFromEntry));
            if (targetDist < 5) continue;

            double tx = cx + dir[0] * targetDist + 0.5;
            double tz = cz + dir[1] * targetDist + 0.5;
            Location candidate = new Location(world, tx, cy, tz);
            Location safe = groundFinder.apply(candidate);

            // Only pick passages that stay underground
            if (safe.getBlockY() >= cy + 5) continue;

            // Candidate fully validated: only now may it update the best
            // pick. (Updating bestAirCount before validation would let a
            // rejected direction shadow later, valid ones.)
            bestAirCount = airCount;
            bestPassage = safe;
        }

        return bestPassage;
    }

    public State getState() { return state; }
    public boolean hasFoundTarget() { return state == State.FOUND_TARGET; }
    public boolean isExhausted() { return state == State.EXHAUSTED; }
    public Location getTargetLocation() { return targetLocation; }
}
