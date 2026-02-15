package com.agentcraft.navigation;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class NavigationController {

    private static final double WALK_SPEED = 0.12;
    private static final double WAYPOINT_REACH_DIST = 0.5;
    private static final long REPATH_COOLDOWN_MS = 2000;
    private static final double REPATH_MOVE_THRESHOLD = 3.0;

    public enum State { IDLE, COMPUTING, FOLLOWING, ARRIVED, FAILED }

    private final AIAgent agent;

    private State state = State.IDLE;
    private Pathfinder pathfinder;
    private List<PathNode> path;
    private int waypointIndex;
    private Location goalLocation;
    private long lastRepathTime;
    private Runnable onArrival;
    private Runnable onFailed;

    public NavigationController(AIAgent agent) {
        this.agent = agent;
    }

    /**
     * Start navigating to a location. Cancels any current navigation.
     */
    public void navigateTo(Location goal, Runnable onArrival, Runnable onFailed) {
        cancel();
        this.goalLocation = goal.clone();
        this.onArrival = onArrival;
        this.onFailed = onFailed;
        startPathfinding();
    }

    public void navigateTo(Location goal) {
        navigateTo(goal, null, null);
    }

    /**
     * Call every tick from BehaviorController.
     */
    public void tick() {
        switch (state) {
            case COMPUTING -> tickComputing();
            case FOLLOWING -> tickFollowing();
            default -> {}
        }
    }

    public void cancel() {
        state = State.IDLE;
        pathfinder = null;
        path = null;
        waypointIndex = 0;
        goalLocation = null;
        onArrival = null;
        onFailed = null;
    }

    public boolean isNavigating() {
        return state == State.COMPUTING || state == State.FOLLOWING;
    }

    public State getState() { return state; }
    public Location getGoalLocation() { return goalLocation; }

    /**
     * If the goal entity has moved significantly, repath.
     * Returns true if a repath was triggered.
     */
    public boolean updateGoalIfMoved(Location newGoal) {
        if (goalLocation == null || !isNavigating()) return false;

        double dist = goalLocation.distanceSquared(newGoal);
        if (dist < REPATH_MOVE_THRESHOLD * REPATH_MOVE_THRESHOLD) return false;

        long now = System.currentTimeMillis();
        if (now - lastRepathTime < REPATH_COOLDOWN_MS) return false;

        goalLocation = newGoal.clone();
        startPathfinding();
        return true;
    }

    private void startPathfinding() {
        Location start = agent.getNpc().getLocation();
        pathfinder = new Pathfinder(start.getWorld(), start, goalLocation);
        state = State.COMPUTING;
        lastRepathTime = System.currentTimeMillis();
    }

    private void tickComputing() {
        if (pathfinder == null) {
            fail();
            return;
        }

        Pathfinder.Status result = pathfinder.step();
        switch (result) {
            case FOUND -> {
                path = pathfinder.getResult();
                pathfinder = null;
                waypointIndex = 1; // skip start node (we're already there)
                state = State.FOLLOWING;
            }
            case NOT_FOUND -> fail();
            case IN_PROGRESS -> {} // keep computing
        }
    }

    private void tickFollowing() {
        if (path == null || waypointIndex >= path.size()) {
            arrive();
            return;
        }

        FakePlayer npc = agent.getNpc();
        Location current = npc.getLocation();

        PathNode waypoint = path.get(waypointIndex);
        double targetX = waypoint.x + 0.5;
        double targetY = waypoint.y;
        double targetZ = waypoint.z + 0.5;

        double dx = targetX - current.getX();
        double dy = targetY - current.getY();
        double dz = targetZ - current.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        // Check if we've reached this waypoint
        if (distXZ < WAYPOINT_REACH_DIST && Math.abs(dy) < 1.5) {
            waypointIndex++;
            if (waypointIndex >= path.size()) {
                arrive();
                return;
            }
            // Update to next waypoint
            waypoint = path.get(waypointIndex);
            targetX = waypoint.x + 0.5;
            targetY = waypoint.y;
            targetZ = waypoint.z + 0.5;
            dx = targetX - current.getX();
            dy = targetY - current.getY();
            dz = targetZ - current.getZ();
            distXZ = Math.sqrt(dx * dx + dz * dz);
        }

        // Move toward current waypoint
        double moveX, moveY, moveZ;
        if (distXZ > WALK_SPEED) {
            double scale = WALK_SPEED / distXZ;
            moveX = dx * scale;
            moveZ = dz * scale;
        } else {
            moveX = dx;
            moveZ = dz;
        }

        // Y movement - snap toward target Y
        if (Math.abs(dy) < 0.1) {
            moveY = 0;
        } else if (dy > 0) {
            moveY = Math.min(dy, 0.5); // step up
        } else {
            moveY = Math.max(dy, -0.5); // step down
        }

        // Look-ahead smoothing: look toward next waypoint if close to current one
        float lookX, lookZ;
        if (distXZ < 1.5 && waypointIndex + 1 < path.size()) {
            PathNode next = path.get(waypointIndex + 1);
            lookX = next.x + 0.5f;
            lookZ = next.z + 0.5f;
        } else {
            lookX = (float) targetX;
            lookZ = (float) targetZ;
        }

        Location lookTarget = new Location(current.getWorld(), lookX, current.getY() + 1.0, lookZ);
        float[] yawPitch = LocationUtil.calculateYawPitch(current, lookTarget);

        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.move(player, moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);
        }
        npc.updatePosition(moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);
    }

    private void arrive() {
        state = State.ARRIVED;
        path = null;
        pathfinder = null;
        Runnable cb = onArrival;
        onArrival = null;
        onFailed = null;
        if (cb != null) cb.run();
        state = State.IDLE;
    }

    private void fail() {
        state = State.FAILED;
        path = null;
        pathfinder = null;
        Runnable cb = onFailed;
        onArrival = null;
        onFailed = null;
        if (cb != null) cb.run();
        state = State.IDLE;
    }
}
