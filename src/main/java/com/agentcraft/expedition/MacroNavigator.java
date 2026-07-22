package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-distance navigation by chaining 40-block A* segments.
 * Force-loads chunks ahead of the NPC and releases them behind.
 */
public class MacroNavigator {

    private static final int SEGMENT_LENGTH = 40;
    private static final int CHUNK_RELEASE_DISTANCE = 64;
    private static final int MAX_CHUNK_TICKETS = 2;
    private static final double ARRIVAL_THRESHOLD = 3.0;
    private static final int MAX_REROUTE_ATTEMPTS = 3;

    public enum State { IDLE, NAVIGATING, ARRIVED, FAILED }

    private final AIAgent agent;
    private final Plugin plugin;

    private State state = State.IDLE;
    private Location ultimateGoal;
    private boolean segmentArrived;
    private boolean segmentFailed;
    private int rerouteAttempts;
    private double totalDistanceTraveled;
    private Location lastTickLocation;
    private final List<Chunk> activeChunkTickets = new ArrayList<>();

    public MacroNavigator(AIAgent agent) {
        this.agent = agent;
        this.plugin = agent.getPlugin();
    }

    public void navigateTo(Location goal) {
        cancel();
        this.ultimateGoal = goal.clone();
        this.state = State.NAVIGATING;
        this.rerouteAttempts = 0;
        this.lastTickLocation = agent.getNpc().getLocation().clone();
        startNextSegment();
    }

    public void cancel() {
        state = State.IDLE;
        ultimateGoal = null;
        segmentArrived = false;
        segmentFailed = false;
        rerouteAttempts = 0;
        agent.getBehaviorController().getNavigation().cancel();
        releaseAllChunkTickets();
    }

    public void tick() {
        if (state != State.NAVIGATING) return;

        // Track distance
        Location currentLoc = agent.getNpc().getLocation();
        if (lastTickLocation != null && lastTickLocation.getWorld().equals(currentLoc.getWorld())) {
            totalDistanceTraveled += lastTickLocation.distance(currentLoc);
        }
        lastTickLocation = currentLoc.clone();

        // Release distant chunk tickets
        releaseDistantChunks(currentLoc);

        // Check if we've reached the ultimate goal
        double distToGoal = LocationUtil.distanceXZ(currentLoc, ultimateGoal);
        if (distToGoal < ARRIVAL_THRESHOLD) {
            state = State.ARRIVED;
            releaseAllChunkTickets();
            return;
        }

        // Handle segment completion
        if (segmentArrived) {
            rerouteAttempts = 0;
            startNextSegment();
        } else if (segmentFailed) {
            rerouteAttempts++;
            if (rerouteAttempts > MAX_REROUTE_ATTEMPTS) {
                state = State.FAILED;
                releaseAllChunkTickets();
                return;
            }
            // Try rerouting at an offset angle
            startReroutedSegment();
        } else if (!agent.getBehaviorController().getNavigation().isNavigating()) {
            // A segment is supposedly in flight but the low-level navigation
            // is idle and neither callback fired: an interrupt (e.g. combat)
            // cancelled the navigation and wiped our callbacks. Re-issue the
            // segment instead of waiting forever.
            startNextSegment();
        }
    }

    private void startNextSegment() {
        Location current = agent.getNpc().getLocation();
        double dx = ultimateGoal.getX() - current.getX();
        double dz = ultimateGoal.getZ() - current.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        Location segmentTarget;
        if (dist <= SEGMENT_LENGTH) {
            segmentTarget = ultimateGoal.clone();
        } else {
            double scale = SEGMENT_LENGTH / dist;
            double tx = current.getX() + dx * scale;
            double tz = current.getZ() + dz * scale;
            segmentTarget = new Location(current.getWorld(), tx, current.getY(), tz);
        }

        navigateToSegment(segmentTarget);
    }

    private void startReroutedSegment() {
        releaseAllChunkTickets();
        Location current = agent.getNpc().getLocation();
        double dx = ultimateGoal.getX() - current.getX();
        double dz = ultimateGoal.getZ() - current.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 1.0) {
            state = State.ARRIVED;
            releaseAllChunkTickets();
            return;
        }

        // Base direction
        double baseAngle = Math.atan2(dz, dx);

        // Offset by 45 or 90 degrees alternating
        double offset = (rerouteAttempts % 2 == 1) ? Math.PI / 4 : Math.PI / 2;
        if (rerouteAttempts > 1) offset = -offset;

        double angle = baseAngle + offset;
        double segDist = Math.min(dist, SEGMENT_LENGTH);
        double tx = current.getX() + Math.cos(angle) * segDist;
        double tz = current.getZ() + Math.sin(angle) * segDist;

        Location segmentTarget = new Location(current.getWorld(), tx, current.getY(), tz);
        navigateToSegment(segmentTarget);
    }

    private void navigateToSegment(Location target) {
        // Force-load the target chunk
        World world = target.getWorld();
        Location safeTarget = LocationUtil.findSafeGround(target);

        Chunk targetChunk = world.getChunkAt(safeTarget);
        if (!targetChunk.isLoaded()) {
            targetChunk.load();
        }
        if (activeChunkTickets.size() < MAX_CHUNK_TICKETS) {
            targetChunk.addPluginChunkTicket(plugin);
            activeChunkTickets.add(targetChunk);
        }

        segmentArrived = false;
        segmentFailed = false;

        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(safeTarget, () -> segmentArrived = true, () -> segmentFailed = true);
    }

    private void releaseDistantChunks(Location currentLoc) {
        activeChunkTickets.removeIf(chunk -> {
            double cx = chunk.getX() * 16 + 8;
            double cz = chunk.getZ() * 16 + 8;
            double dist = Math.sqrt(
                    Math.pow(cx - currentLoc.getX(), 2) + Math.pow(cz - currentLoc.getZ(), 2));
            if (dist > CHUNK_RELEASE_DISTANCE) {
                chunk.removePluginChunkTicket(plugin);
                return true;
            }
            return false;
        });
    }

    private void releaseAllChunkTickets() {
        for (Chunk chunk : activeChunkTickets) {
            chunk.removePluginChunkTicket(plugin);
        }
        activeChunkTickets.clear();
    }

    public State getState() { return state; }
    public boolean isNavigating() { return state == State.NAVIGATING; }
    public boolean hasArrived() { return state == State.ARRIVED; }
    public boolean hasFailed() { return state == State.FAILED; }
    public double getTotalDistanceTraveled() { return totalDistanceTraveled; }
}
