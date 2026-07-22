package com.agentcraft.navigation;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NavigationController {

    private static final double WALK_SPEED = 0.12;
    private static final double WAYPOINT_REACH_DIST = 0.5;
    private static final long REPATH_COOLDOWN_MS = 2000;
    private static final double REPATH_MOVE_THRESHOLD = 3.0;

    // Natural movement constants
    private static final double WOBBLE_AMPLITUDE = 0.012;
    private static final int ARM_SWING_MIN = 6;
    private static final int ARM_SWING_MAX = 14;
    private static final int GLANCE_MIN_TICKS = 10;  // 0.5s
    private static final int GLANCE_MAX_TICKS = 30;  // 1.5s
    private static final int GLANCE_COOLDOWN_MIN = 60;
    private static final int GLANCE_COOLDOWN_MAX = 200;
    private static final float GLANCE_YAW_MAX = 10.0f;

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

    // Natural movement state
    private int walkTick;
    private int armSwingCooldown;
    private int headGlanceCooldown;
    private int glanceDuration;
    private float glanceYawOffset;

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
        walkTick = 0;
        armSwingCooldown = 0;
        headGlanceCooldown = 0;
        glanceDuration = 0;
        glanceYawOffset = 0;
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

        walkTick++;
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
        double moveX, moveZ;
        if (distXZ > WALK_SPEED) {
            double scale = WALK_SPEED / distXZ;
            moveX = dx * scale;
            moveZ = dz * scale;
        } else {
            moveX = dx;
            moveZ = dz;
        }

        // Sinusoidal perpendicular wobble for natural-looking movement
        double wobble = Math.sin(walkTick * 0.35) * WOBBLE_AMPLITUDE;
        // Perpendicular vector: rotate (moveX, moveZ) by 90 degrees
        double perpX = -moveZ;
        double perpZ = moveX;
        double perpLen = Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (perpLen > 0.001) {
            moveX += (perpX / perpLen) * wobble;
            moveZ += (perpZ / perpLen) * wobble;
        }

        // Y movement - snap toward target Y
        double moveY;
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

        // Head glance: occasionally offset yaw while walking
        if (glanceDuration > 0) {
            yawPitch[0] += glanceYawOffset;
            glanceDuration--;
        } else if (--headGlanceCooldown <= 0) {
            headGlanceCooldown = ThreadLocalRandom.current().nextInt(GLANCE_COOLDOWN_MIN, GLANCE_COOLDOWN_MAX + 1);
            glanceDuration = ThreadLocalRandom.current().nextInt(GLANCE_MIN_TICKS, GLANCE_MAX_TICKS + 1);
            glanceYawOffset = (ThreadLocalRandom.current().nextFloat() * 2 - 1) * GLANCE_YAW_MAX;
        }

        // Arm swing while walking
        boolean swing = false;
        if (--armSwingCooldown <= 0) {
            armSwingCooldown = ThreadLocalRandom.current().nextInt(ARM_SWING_MIN, ARM_SWING_MAX + 1);
            swing = true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.move(player, moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);
            if (swing) {
                npc.swingArm(player);
            }
        }
        npc.updatePosition(moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);
    }

    private void arrive() {
        path = null;
        pathfinder = null;
        Runnable cb = onArrival;
        onArrival = null;
        onFailed = null;
        // Go IDLE *before* invoking the callback: a callback may start a new
        // navigation (chained navigateTo), and setting IDLE afterwards would
        // silently clobber it.
        state = State.IDLE;
        if (cb != null) cb.run();
    }

    private void fail() {
        path = null;
        pathfinder = null;
        Runnable cb = onFailed;
        onArrival = null;
        onFailed = null;
        // See arrive(): IDLE must be set before the callback runs so a
        // navigation started inside the callback is not clobbered.
        state = State.IDLE;
        if (cb != null) cb.run();
    }
}
