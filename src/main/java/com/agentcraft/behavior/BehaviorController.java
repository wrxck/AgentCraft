package com.agentcraft.behavior;

import com.agentcraft.action.*;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.agent.AgentState;
import com.agentcraft.expedition.ExpeditionController;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BehaviorController {

    // Movement
    private static final double WALK_SPEED = 0.12;
    private static final double RUN_SPEED = 0.24;
    private static final double WANDER_RADIUS = 4.0;
    private static final double MAX_WANDER_FROM_HOME = 6.0;
    private static final double APPROACH_STOP = 3.0;

    // Awareness
    private static final double LOOK_RANGE = 12.0;
    private static final double FLEE_DETECTION_RANGE = 10.0;

    // Flee timing (in ticks)
    private static final int FLEE_DURATION_MIN = 60;   // 3s
    private static final int FLEE_DURATION_MAX = 120;   // 6s
    private static final int FLEE_DISTANCE = 12;

    // Hostile mob types the NPC will flee from
    private static final Set<EntityType> HOSTILE_MOBS = EnumSet.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.WITCH, EntityType.ENDERMAN,
            EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.HUSK,
            EntityType.STRAY, EntityType.PHANTOM, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.VEX,
            EntityType.EVOKER, EntityType.BLAZE, EntityType.GHAST,
            EntityType.WITHER_SKELETON, EntityType.SLIME, EntityType.MAGMA_CUBE
    );

    // Timing (in ticks)
    private static final int IDLE_LOOK_MIN = 40;   // 2s
    private static final int IDLE_LOOK_MAX = 120;   // 6s
    private static final int WANDER_MIN = 200;      // 10s
    private static final int WANDER_MAX = 600;      // 30s
    private static final int WORK_GLANCE_MIN = 60;  // 3s
    private static final int WORK_GLANCE_MAX = 160;  // 8s
    private static final int GATHER_TICK_INTERVAL = 4; // ticks between break animation stages
    private static final int GATHER_STAGES = 10;       // stages 0-9

    // Blocks the NPC can instantly harvest
    private static final Set<Material> HARVESTABLE_BLOCKS = EnumSet.of(
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID,
            Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP,
            Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
            Material.DEAD_BUSH,
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.SWEET_BERRY_BUSH,
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS
    );

    private final AIAgent agent;
    private final Location homeLocation;
    private final NavigationController navigation;
    private final AutonomousController autonomous;

    private BukkitRunnable tickTask;
    private Location wanderTarget;
    private UUID taskRequesterId;
    private boolean approachedRequester;
    private boolean returningHome;
    private AgentState lastState;
    private int nextBehaviorTick;
    private int tickCounter;

    // Resource gathering
    private final List<ItemStack> inventory = new ArrayList<>();
    private Location gatherTarget;
    private int gatherStage = -1;
    private int gatherTickCooldown;
    private IdleBehavior currentIdleBehavior = IdleBehavior.STANDING;

    // Giving
    private Player giveTarget;

    // Following
    private Player followTarget;

    // Flee/sprint
    private Location fleeTarget;
    private int fleeTicksRemaining;
    private boolean sprinting;
    private int sprintTicksRemaining;
    private int threatScanCooldown;

    // Expedition
    private ExpeditionController activeExpedition;

    // Walk wobble/swing for short-distance direct movement
    private int walkSwingCooldown;
    private int directWalkTick;

    public BehaviorController(AIAgent agent) {
        this.agent = agent;
        this.homeLocation = agent.getNpc().getLocation().clone();
        this.navigation = new NavigationController(agent);
        this.lastState = AgentState.IDLE;
        this.nextBehaviorTick = randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);

        // Autonomous behavior from config
        boolean autoEnabled = agent.getPlugin().getConfig().getBoolean("autonomous.enabled", true);
        int autoInterval = agent.getPlugin().getConfig().getInt("autonomous.interval-seconds", 60);
        int autoMaxTurns = agent.getPlugin().getConfig().getInt("autonomous.max-turns-per-cycle", 10);
        this.autonomous = new AutonomousController(agent, autoEnabled, autoInterval, autoMaxTurns);
    }

    public NavigationController getNavigation() {
        return navigation;
    }

    public AutonomousController getAutonomous() {
        return autonomous;
    }

    /**
     * Check if the agent is busy with any sub-behavior.
     */
    public boolean isBusy() {
        if (navigation.isNavigating()) return true;
        if (agent.getActionQueue().isRunning()) return true;
        if (activeExpedition != null) return true;
        if (autonomous.isThinking()) return true;
        if (wanderTarget != null) return true;
        if (gatherTarget != null) return true;
        if (followTarget != null) return true;
        if (fleeTarget != null) return true;
        if (giveTarget != null) return true;
        return false;
    }

    public void start() {
        if (tickTask != null) return;

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        tickTask.runTaskTimer(agent.getPlugin(), 20L, 1L); // 1s delay before starting
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        // Navigation must tick even during action queue (actions use navigation)
        navigation.tick();

        // Autonomous think cycle
        autonomous.tick();

        // Expedition takes over all behavior when active
        if (activeExpedition != null) {
            activeExpedition.tick();
            if (activeExpedition.isComplete()) {
                activeExpedition = null;
                agent.setState(AgentState.IDLE);
            }
            return;
        }

        // Don't run behavior logic while scripted actions are running
        if (agent.getActionQueue().isRunning()) return;

        AgentState currentState = agent.getState();

        // Detect state transitions
        if (currentState != lastState) {
            onStateTransition(lastState, currentState);
            lastState = currentState;
        }

        tickCounter++;

        switch (currentState) {
            case IDLE -> tickIdle();
            case THINKING -> tickThinking();
            case WORKING -> tickWorking();
            default -> {}
        }
    }

    private void onStateTransition(AgentState from, AgentState to) {
        switch (to) {
            case IDLE -> {
                wanderTarget = null;
                resetGatherState();
                giveTarget = null;
                followTarget = null;
                navigation.cancel();
                currentIdleBehavior = IdleBehavior.STANDING;
                // Return home if we've drifted
                double distFromHome = LocationUtil.distanceXZ(agent.getNpc().getLocation(), homeLocation);
                if (distFromHome > 1.5) {
                    returningHome = true;
                }
                nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            }
            case THINKING -> {
                approachedRequester = false;
                wanderTarget = null;
                resetGatherState();
                giveTarget = null;
                followTarget = null;
                navigation.cancel();
                currentIdleBehavior = IdleBehavior.STANDING;
                nextBehaviorTick = tickCounter; // act immediately
            }
            default -> {}
        }
    }

    // --- State tick methods ---

    private void tickIdle() {
        // Tick sprint timer
        if (sprinting && --sprintTicksRemaining <= 0) {
            sprinting = false;
        }

        // Priority: fleeing overrides everything
        if (currentIdleBehavior == IdleBehavior.FLEEING) {
            tickFleeing();
            return;
        }

        // Periodic threat scanning (every 20 ticks = 1 second)
        if (--threatScanCooldown <= 0) {
            threatScanCooldown = 20;
            Entity threat = scanForThreats();
            if (threat != null) {
                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll < 0.15) {
                    // 15% chance to flee from hostile mob
                    startFleeing(threat.getLocation());
                    return;
                }
            }
            // 3% chance to flee from a nearby player (rare, funny)
            Player nearestPlayer = findNearestPlayer();
            if (nearestPlayer != null && ThreadLocalRandom.current().nextDouble() < 0.03) {
                startFleeing(nearestPlayer.getLocation());
                return;
            }
        }

        // Following behavior
        if (currentIdleBehavior == IdleBehavior.FOLLOWING) {
            tickFollowing();
            return;
        }

        // Priority: return home first
        if (returningHome) {
            if (walkToward(homeLocation, sprinting ? RUN_SPEED : WALK_SPEED)) {
                returningHome = false;
                nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            }
            return;
        }

        // Continue active sub-behaviors
        if (currentIdleBehavior == IdleBehavior.GATHERING) {
            tickGathering();
            return;
        }
        if (currentIdleBehavior == IdleBehavior.GIVING) {
            tickGiving();
            return;
        }

        // Walking to wander target
        if (wanderTarget != null) {
            if (walkToward(wanderTarget, sprinting ? RUN_SPEED : WALK_SPEED)) {
                wanderTarget = null;
                nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            }
            return;
        }

        // Wait for cooldown
        if (tickCounter < nextBehaviorTick) return;

        // Pick weighted random behavior
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.20) {
            // Look at nearest player
            Player nearest = findNearestPlayer();
            if (nearest != null) {
                lookAtPlayer(nearest);
            }
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
        } else if (roll < 0.35) {
            // Look at random direction
            lookAtRandomDirection();
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
        } else if (roll < 0.45) {
            // Wander
            Location target = pickWanderTarget();
            if (target != null) {
                wanderTarget = target;
            } else {
                nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            }
        } else if (roll < 0.80) {
            // Gather resources
            startGathering();
        } else if (roll < 0.90) {
            // Give items to player
            startGiving();
        } else {
            // Stand still
            nextBehaviorTick = tickCounter + randomRange(WANDER_MIN, WANDER_MAX);
        }
    }

    private void tickThinking() {
        if (taskRequesterId != null && !approachedRequester) {
            Player requester = Bukkit.getPlayer(taskRequesterId);
            if (requester != null && requester.isOnline()) {
                Location reqLoc = requester.getLocation();
                double dist = LocationUtil.distanceXZ(agent.getNpc().getLocation(), reqLoc);

                if (dist > APPROACH_STOP) {
                    walkToward(reqLoc);
                    return;
                } else {
                    approachedRequester = true;
                    lookAtPlayer(requester);
                    return;
                }
            }
        }

        // Periodically look at nearest player
        if (tickCounter >= nextBehaviorTick) {
            Player nearest = findNearestPlayer();
            if (nearest != null) {
                lookAtPlayer(nearest);
            }
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
        }
    }

    private void tickWorking() {
        // Periodically glance at nearest player
        if (tickCounter >= nextBehaviorTick) {
            Player nearest = findNearestPlayer();
            if (nearest != null) {
                lookAtPlayer(nearest);
            }
            nextBehaviorTick = tickCounter + randomRange(WORK_GLANCE_MIN, WORK_GLANCE_MAX);
        }
    }

    // --- Movement helpers ---

    /**
     * Move at WALK_SPEED toward target. Returns true on arrival.
     * For distances >3 blocks, delegates to NavigationController for A* pathfinding.
     * For short distances, uses direct vector movement with wobble.
     */
    private boolean walkToward(Location target) {
        return walkToward(target, WALK_SPEED);
    }

    /**
     * Move at given speed toward target. Returns true on arrival.
     * Delegates to NavigationController for long distances (>3 blocks).
     */
    private boolean walkToward(Location target, double speed) {
        FakePlayer npc = agent.getNpc();
        Location current = npc.getLocation();
        double distXZ = LocationUtil.distanceXZ(current, target);

        if (distXZ <= 0.3) {
            return true;
        }

        // For long distances, use A* pathfinding via NavigationController
        if (distXZ > 3.0 && !navigation.isNavigating()) {
            navigation.navigateTo(target);
            return false;
        }
        if (navigation.isNavigating()) {
            // Navigation is handling movement; check if arrived
            if (navigation.getState() == NavigationController.State.ARRIVED
                    || navigation.getState() == NavigationController.State.IDLE) {
                return distXZ <= 0.3;
            }
            if (navigation.getState() == NavigationController.State.FAILED) {
                // Fall through to direct movement as fallback
                navigation.cancel();
            } else {
                return false; // still navigating
            }
        }

        // Short-distance direct movement with wobble
        return walkTowardDirect(target, speed);
    }

    /**
     * Direct vector movement for short distances. Includes wobble and arm swing.
     */
    private boolean walkTowardDirect(Location target, double speed) {
        FakePlayer npc = agent.getNpc();
        Location current = npc.getLocation();

        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        if (distXZ <= 0.3) {
            return true;
        }

        directWalkTick++;

        // Normalize and scale
        double scale = speed / distXZ;
        double moveX = dx * scale;
        double moveZ = dz * scale;

        // Clamp to remaining distance
        if (Math.abs(moveX) > Math.abs(dx)) moveX = dx;
        if (Math.abs(moveZ) > Math.abs(dz)) moveZ = dz;

        // Sinusoidal perpendicular wobble
        double wobble = Math.sin(directWalkTick * 0.35) * 0.012;
        double perpX = -moveZ;
        double perpZ = moveX;
        double perpLen = Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (perpLen > 0.001) {
            moveX += (perpX / perpLen) * wobble;
            moveZ += (perpZ / perpLen) * wobble;
        }

        // Find safe Y at destination
        Location nextPos = current.clone().add(moveX, 0, moveZ);
        Location safe = LocationUtil.findSafeGround(nextPos);
        double moveY = safe.getY() - current.getY();

        // Clamp Y movement per tick
        if (moveY > 0.5) moveY = 0.5;
        if (moveY < -0.5) moveY = -0.5;

        float[] yawPitch = LocationUtil.calculateYawPitch(current, target);

        // Arm swing while walking
        boolean swing = false;
        if (--walkSwingCooldown <= 0) {
            walkSwingCooldown = ThreadLocalRandom.current().nextInt(6, 15);
            swing = true;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.move(player, moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);
            if (swing) {
                npc.swingArm(player);
            }
        }
        npc.updatePosition(moveX, moveY, moveZ, yawPitch[0], yawPitch[1]);

        return false;
    }

    private Location pickWanderTarget() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Random angle and distance within WANDER_RADIUS
        double angle = rand.nextDouble() * 2 * Math.PI;
        double distance = rand.nextDouble() * WANDER_RADIUS;

        double x = homeLocation.getX() + Math.cos(angle) * distance;
        double z = homeLocation.getZ() + Math.sin(angle) * distance;

        Location candidate = new Location(homeLocation.getWorld(), x, homeLocation.getY(), z);
        Location safe = LocationUtil.findSafeGround(candidate);

        // Verify Y is within 3 blocks of home
        if (Math.abs(safe.getY() - homeLocation.getY()) > 3) {
            return null;
        }

        // Verify not too far from home
        if (LocationUtil.distanceXZ(safe, homeLocation) > MAX_WANDER_FROM_HOME) {
            return null;
        }

        return safe;
    }

    // --- Gathering behavior ---

    private void startGathering() {
        Location npcLoc = agent.getNpc().getLocation();
        Block nearest = findNearestHarvestable(npcLoc);
        if (nearest == null) {
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            return;
        }
        gatherTarget = nearest.getLocation().add(0.5, 0, 0.5); // center of block
        gatherStage = -1;
        gatherTickCooldown = 0;
        currentIdleBehavior = IdleBehavior.GATHERING;
    }

    private void tickGathering() {
        if (gatherTarget == null) {
            finishGathering();
            return;
        }

        // Check the block is still harvestable
        Block block = gatherTarget.getBlock();
        if (!HARVESTABLE_BLOCKS.contains(block.getType())) {
            finishGathering();
            return;
        }

        FakePlayer npc = agent.getNpc();
        double dist = LocationUtil.distanceXZ(npc.getLocation(), gatherTarget);

        // Walk to block
        if (dist > 1.5) {
            walkToward(gatherTarget);
            return;
        }

        // Look at the block
        Location blockCenter = gatherTarget.clone().add(0, 0.5, 0);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, blockCenter);
        }

        // Progress break animation on cooldown
        if (gatherTickCooldown > 0) {
            gatherTickCooldown--;
            return;
        }

        gatherStage++;
        gatherTickCooldown = GATHER_TICK_INTERVAL;

        if (gatherStage < GATHER_STAGES) {
            // Swing arm and show break animation
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.swingArm(viewer);
                npc.breakBlockAnimation(viewer, gatherTarget, gatherStage);
            }
        } else {
            // Finished breaking — cancel animation, break block, collect drops
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.breakBlockAnimation(viewer, gatherTarget, -1);
            }
            block.breakNaturally();

            // Capture location before finishGathering nulls gatherTarget
            Location dropLoc = gatherTarget.clone();

            // Collect nearby item entities
            Bukkit.getScheduler().runTaskLater(agent.getPlugin(), () -> {
                for (Entity entity : dropLoc.getWorld().getNearbyEntities(dropLoc, 2, 2, 2)) {
                    if (entity instanceof Item item) {
                        inventory.add(item.getItemStack().clone());
                        item.remove();
                    }
                }
            }, 5L); // slight delay for drops to spawn

            finishGathering();
        }
    }

    private void finishGathering() {
        resetGatherState();
        currentIdleBehavior = IdleBehavior.STANDING;
        nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
    }

    private void resetGatherState() {
        if (gatherTarget != null && gatherStage >= 0) {
            // Cancel any in-progress break animation
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                agent.getNpc().breakBlockAnimation(viewer, gatherTarget, -1);
            }
        }
        gatherTarget = null;
        gatherStage = -1;
        gatherTickCooldown = 0;
    }

    private Block findNearestHarvestable(Location center) {
        int radius = (int) WANDER_RADIUS;
        Block nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.getWorld().getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (HARVESTABLE_BLOCKS.contains(b.getType())) {
                        double distSq = b.getLocation().distanceSquared(center);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = b;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    // --- Giving behavior ---

    private void startGiving() {
        if (inventory.isEmpty()) {
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            return;
        }
        Player nearest = findNearestPlayer();
        if (nearest == null) {
            nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
            return;
        }
        giveTarget = nearest;
        currentIdleBehavior = IdleBehavior.GIVING;
    }

    private void tickGiving() {
        if (giveTarget == null || !giveTarget.isOnline() || inventory.isEmpty()) {
            finishGiving();
            return;
        }

        FakePlayer npc = agent.getNpc();
        Location playerLoc = giveTarget.getLocation();
        double dist = LocationUtil.distanceXZ(npc.getLocation(), playerLoc);

        // Walk toward player
        if (dist > 2.0) {
            walkToward(playerLoc);
            return;
        }

        // Look at player
        lookAtPlayer(giveTarget);

        // Drop 1-3 items at player's feet
        int count = Math.min(ThreadLocalRandom.current().nextInt(1, 4), inventory.size());
        for (int i = 0; i < count; i++) {
            ItemStack stack = inventory.remove(inventory.size() - 1);
            giveTarget.getWorld().dropItem(playerLoc, stack);
        }

        finishGiving();
    }

    private void finishGiving() {
        giveTarget = null;
        currentIdleBehavior = IdleBehavior.STANDING;
        nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
    }

    // --- Look helpers ---

    private Player findNearestPlayer() {
        Location npcLoc = agent.getNpc().getLocation();
        Player nearest = null;
        double nearestDistSq = LOOK_RANGE * LOOK_RANGE;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(npcLoc.getWorld())) continue;
            double distSq = player.getLocation().distanceSquared(npcLoc);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = player;
            }
        }

        return nearest;
    }

    private void lookAtPlayer(Player target) {
        FakePlayer npc = agent.getNpc();
        Location eyeTarget = target.getEyeLocation();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, eyeTarget);
        }
    }

    private void lookAtRandomDirection() {
        FakePlayer npc = agent.getNpc();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Random yaw, slight pitch variation
        float yaw = rand.nextFloat() * 360 - 180;
        float pitch = rand.nextFloat() * 30 - 15;

        Location fakeLookTarget = npc.getLocation().clone();
        double rad = Math.toRadians(yaw);
        fakeLookTarget.add(-Math.sin(rad) * 5, 0, Math.cos(rad) * 5);
        fakeLookTarget.setY(fakeLookTarget.getY() + 1.6 - Math.tan(Math.toRadians(pitch)) * 5);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, fakeLookTarget);
        }
    }

    // --- Flee behavior ---

    private Entity scanForThreats() {
        Location npcLoc = agent.getNpc().getLocation();
        Entity nearest = null;
        double nearestDistSq = FLEE_DETECTION_RANGE * FLEE_DETECTION_RANGE;

        for (Entity entity : npcLoc.getWorld().getNearbyEntities(npcLoc,
                FLEE_DETECTION_RANGE, FLEE_DETECTION_RANGE, FLEE_DETECTION_RANGE)) {
            if (!HOSTILE_MOBS.contains(entity.getType())) continue;
            double distSq = entity.getLocation().distanceSquared(npcLoc);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = entity;
            }
        }
        return nearest;
    }

    private void startFleeing(Location threatLoc) {
        Location npcLoc = agent.getNpc().getLocation();

        // Calculate direction away from threat
        double dx = npcLoc.getX() - threatLoc.getX();
        double dz = npcLoc.getZ() - threatLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double fleeX, fleeZ;
        if (dist > 0.1) {
            fleeX = npcLoc.getX() + (dx / dist) * FLEE_DISTANCE;
            fleeZ = npcLoc.getZ() + (dz / dist) * FLEE_DISTANCE;
        } else {
            // Threat is right on top of us, pick random direction
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            fleeX = npcLoc.getX() + Math.cos(angle) * FLEE_DISTANCE;
            fleeZ = npcLoc.getZ() + Math.sin(angle) * FLEE_DISTANCE;
        }

        Location candidate = new Location(npcLoc.getWorld(), fleeX, npcLoc.getY(), fleeZ);
        fleeTarget = LocationUtil.findSafeGround(candidate);
        fleeTicksRemaining = randomRange(FLEE_DURATION_MIN, FLEE_DURATION_MAX);
        currentIdleBehavior = IdleBehavior.FLEEING;
        sprinting = true;
        sprintTicksRemaining = fleeTicksRemaining + 20;
        wanderTarget = null;
        resetGatherState();
        giveTarget = null;
    }

    private void tickFleeing() {
        if (fleeTarget == null || --fleeTicksRemaining <= 0) {
            finishFleeing();
            return;
        }
        if (walkToward(fleeTarget, RUN_SPEED)) {
            finishFleeing();
        }
    }

    private void finishFleeing() {
        fleeTarget = null;
        fleeTicksRemaining = 0;
        currentIdleBehavior = IdleBehavior.STANDING;
        returningHome = true;
        nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
    }

    // --- Following behavior ---

    public void startFollowing(Player player) {
        if (player == null) return;
        followTarget = player;
        currentIdleBehavior = IdleBehavior.FOLLOWING;
        wanderTarget = null;
        resetGatherState();
        giveTarget = null;
    }

    private void tickFollowing() {
        if (followTarget == null || !followTarget.isOnline()
                || !followTarget.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
            stopFollowing();
            return;
        }

        Location targetLoc = followTarget.getLocation();
        double dist = LocationUtil.distanceXZ(agent.getNpc().getLocation(), targetLoc);

        // Close enough — just look at them
        if (dist <= APPROACH_STOP) {
            lookAtPlayer(followTarget);
            if (navigation.isNavigating()) {
                navigation.cancel();
            }
            return;
        }

        // Use navigation for pathfinding
        navigation.updateGoalIfMoved(targetLoc);
        if (!navigation.isNavigating()) {
            navigation.navigateTo(targetLoc);
        }
    }

    private void stopFollowing() {
        followTarget = null;
        navigation.cancel();
        currentIdleBehavior = IdleBehavior.STANDING;
        nextBehaviorTick = tickCounter + randomRange(IDLE_LOOK_MIN, IDLE_LOOK_MAX);
    }

    // --- Inventory helpers ---

    public void addToInventory(ItemStack item) {
        inventory.add(item);
    }

    public boolean removeFromInventory(Material material) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).getType() == material) {
                ItemStack stack = inventory.get(i);
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    inventory.remove(i);
                }
                return true;
            }
        }
        return false;
    }

    public boolean hasInInventory(Material material) {
        for (ItemStack stack : inventory) {
            if (stack.getType() == material) return true;
        }
        return false;
    }

    /**
     * Count how many of a material are in inventory.
     */
    public int countInInventory(Material material) {
        int total = 0;
        for (ItemStack stack : inventory) {
            if (stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    /**
     * Remove up to count of a material from inventory. Returns actual amount removed.
     */
    public int removeFromInventory(Material material, int count) {
        int remaining = count;
        Iterator<ItemStack> it = inventory.iterator();
        while (it.hasNext() && remaining > 0) {
            ItemStack stack = it.next();
            if (stack.getType() != material) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                it.remove();
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        return count - remaining;
    }

    // --- AI-triggered actions ---

    /**
     * Execute an action command from the AI chat response.
     * Called from ConversationManager when AI outputs "> command".
     */
    public void executeAction(String action) {
        if (action == null || action.isEmpty()) return;

        String[] parts = action.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : null;

        switch (command) {
            case "flee" -> {
                Entity threat = scanForThreats();
                if (threat != null) {
                    startFleeing(threat.getLocation());
                } else {
                    double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                    Location fakeThreat = agent.getNpc().getLocation().clone()
                            .add(-Math.cos(angle) * 5, 0, -Math.sin(angle) * 5);
                    startFleeing(fakeThreat);
                }
            }
            case "sprint" -> {
                sprinting = true;
                sprintTicksRemaining = randomRange(60, 100);
            }
            case "approach" -> {
                if (arg != null) {
                    Player target = Bukkit.getPlayerExact(arg);
                    if (target != null && target.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
                        navigation.navigateTo(target.getLocation());
                    }
                }
            }
            case "wander" -> {
                Location target = pickWanderTarget();
                if (target != null) {
                    wanderTarget = target;
                }
            }
            case "look" -> {
                if (arg != null) {
                    Player target = Bukkit.getPlayerExact(arg);
                    if (target != null) {
                        lookAtPlayer(target);
                    }
                }
            }
            case "idle" -> {
                wanderTarget = null;
                resetGatherState();
                giveTarget = null;
                followTarget = null;
                fleeTarget = null;
                sprinting = false;
                navigation.cancel();
                currentIdleBehavior = IdleBehavior.STANDING;
            }
            case "goto" -> {
                if (arg != null) {
                    Location target = parseGotoTarget(arg);
                    if (target != null) {
                        navigation.navigateTo(target);
                    }
                }
            }
            case "follow" -> {
                if (arg != null) {
                    Player target = Bukkit.getPlayerExact(arg);
                    if (target != null) {
                        startFollowing(target);
                    }
                }
            }
            case "home" -> {
                navigation.navigateTo(homeLocation);
            }
            case "mine" -> {
                if (arg != null) {
                    parseMineAction(arg);
                }
            }
            case "gather" -> {
                if (arg != null) {
                    parseGatherAction(arg);
                }
            }
            case "place" -> {
                if (arg != null) {
                    parsePlaceAction(arg);
                }
            }
            case "attack" -> {
                if (arg != null) {
                    parseAttackAction(arg);
                }
            }
            case "eat" -> {
                // Consume a food item from inventory for flavor (no actual hunger system)
                for (int i = 0; i < inventory.size(); i++) {
                    Material mat = inventory.get(i).getType();
                    if (mat.isEdible()) {
                        removeFromInventory(mat);
                        for (Player viewer : Bukkit.getOnlinePlayers()) {
                            agent.getNpc().swingArm(viewer);
                        }
                        break;
                    }
                }
            }
            case "expedition" -> {
                if (arg != null) {
                    parseExpeditionAction(arg);
                }
            }
            case "drop" -> {
                if (arg != null && !inventory.isEmpty()) {
                    String matName = arg.trim().toUpperCase().replace(' ', '_');
                    Material targetMat = null;
                    try {
                        targetMat = Material.valueOf(matName);
                    } catch (IllegalArgumentException ignored) {}

                    if (targetMat != null && removeFromInventory(targetMat)) {
                        Location npcLoc = agent.getNpc().getLocation();
                        npcLoc.getWorld().dropItem(npcLoc, new ItemStack(targetMat));
                    }
                } else if (!inventory.isEmpty()) {
                    // Drop first item
                    ItemStack stack = inventory.remove(0);
                    Location npcLoc = agent.getNpc().getLocation();
                    npcLoc.getWorld().dropItem(npcLoc, stack);
                }
            }
        }
    }

    // --- Action parsers ---

    private Location parseGotoTarget(String arg) {
        // Try "x y z" format
        String[] coords = arg.trim().split("\\s+");
        if (coords.length >= 2) {
            try {
                int x = Integer.parseInt(coords[0]);
                int z;
                int y;
                if (coords.length >= 3) {
                    y = Integer.parseInt(coords[1]);
                    z = Integer.parseInt(coords[2]);
                } else {
                    // x z only — find ground Y
                    z = Integer.parseInt(coords[1]);
                    y = agent.getNpc().getLocation().getBlockY();
                }
                Location loc = new Location(agent.getNpc().getLocation().getWorld(), x + 0.5, y, z + 0.5);
                return LocationUtil.findSafeGround(loc);
            } catch (NumberFormatException ignored) {}
        }

        // Try player name
        Player target = Bukkit.getPlayerExact(arg.trim());
        if (target != null && target.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
            return target.getLocation();
        }

        // Try NPC name
        for (AIAgent other : AgentManager.getInstance().getAllAgents()) {
            if (other != agent && other.getNpc().getName().equalsIgnoreCase(arg.trim())) {
                return other.getNpc().getLocation();
            }
        }

        return null;
    }

    private void parseMineAction(String arg) {
        // Try "x y z" coordinates
        String[] coords = arg.trim().split("\\s+");
        Location blockLoc = null;
        if (coords.length >= 3) {
            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                blockLoc = new Location(agent.getNpc().getLocation().getWorld(), x, y, z);
            } catch (NumberFormatException ignored) {}
        }

        // Try material name — find nearest matching block
        if (blockLoc == null) {
            String matName = arg.trim().toUpperCase().replace(' ', '_');
            blockLoc = findNearestBlockByMaterial(matName);
        }

        if (blockLoc != null) {
            MineAction mineAction = new MineAction(agent, blockLoc);
            agent.getActionQueue().add(mineAction);
            agent.getActionQueue().start();
        }
    }

    private void parsePlaceAction(String arg) {
        // format: "material x y z"
        String[] parts = arg.trim().split("\\s+");
        if (parts.length < 4) return;

        String matName = parts[0].toUpperCase().replace(' ', '_');
        Material material;
        try {
            material = Material.valueOf(matName);
        } catch (IllegalArgumentException e) { return; }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            Location blockLoc = new Location(agent.getNpc().getLocation().getWorld(), x, y, z);

            PlaceAction placeAction = new PlaceAction(agent, blockLoc, material);
            agent.getActionQueue().add(placeAction);
            agent.getActionQueue().start();
        } catch (NumberFormatException ignored) {}
    }

    private void parseAttackAction(String arg) {
        String targetName = arg.trim();
        Location npcLoc = agent.getNpc().getLocation();

        // Find nearest entity matching the name (not players)
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : npcLoc.getWorld().getNearbyEntities(npcLoc, 16, 8, 16)) {
            if (entity instanceof Player) continue; // never attack players
            if (!(entity instanceof LivingEntity)) continue;

            String entityName = entity.getType().name().toLowerCase().replace('_', ' ');
            if (entityName.contains(targetName.toLowerCase())) {
                double dist = entity.getLocation().distanceSquared(npcLoc);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entity;
                }
            }
        }

        if (best instanceof LivingEntity living) {
            AttackAction attackAction = new AttackAction(agent, living);
            agent.getActionQueue().add(attackAction);
            agent.getActionQueue().start();
        }
    }

    private void parseGatherAction(String arg) {
        // Format: "material [count]" e.g. "oak_log 10" or "oak_log" (defaults to 16)
        String[] parts = arg.trim().split("\\s+");
        String matName = parts[0];
        int count = 16; // default

        if (parts.length >= 2) {
            try {
                count = Integer.parseInt(parts[parts.length - 1]);
                // Rebuild material name from non-numeric parts
                StringBuilder matBuilder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (matBuilder.length() > 0) matBuilder.append("_");
                    matBuilder.append(parts[i]);
                }
                matName = matBuilder.toString();
            } catch (NumberFormatException e) {
                // Last part isn't a number, whole thing is material name
                matName = String.join("_", parts);
            }
        }

        GatherAction gatherAction = new GatherAction(agent, matName, count);
        agent.getActionQueue().add(gatherAction);
        agent.getActionQueue().start();
    }

    private Location findNearestBlockByMaterial(String matName) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX();
        int cy = npcLoc.getBlockY();
        int cz = npcLoc.getBlockZ();
        int radius = 16;

        Block nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (b.getType().name().contains(matName) || b.getType().name().equalsIgnoreCase(matName)) {
                        double distSq = b.getLocation().distanceSquared(npcLoc);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = b;
                        }
                    }
                }
            }
        }

        return nearest != null ? nearest.getLocation() : null;
    }

    // --- Expedition management ---

    private void parseExpeditionAction(String arg) {
        String[] parts = arg.trim().split("\\s+");
        String material = parts[0];
        int count = 16;
        if (parts.length >= 2) {
            try {
                count = Integer.parseInt(parts[parts.length - 1]);
                StringBuilder matBuilder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (matBuilder.length() > 0) matBuilder.append("_");
                    matBuilder.append(parts[i]);
                }
                material = matBuilder.toString();
            } catch (NumberFormatException e) {
                material = String.join("_", parts);
            }
        }

        Player owner = taskRequesterId != null ? Bukkit.getPlayer(taskRequesterId) : null;
        if (owner == null) {
            // Find nearest player as fallback
            owner = findNearestPlayer();
        }
        if (owner == null) return;

        startExpedition(new ExpeditionController(agent, owner, material, count));
    }

    public void startExpedition(ExpeditionController expedition) {
        if (activeExpedition != null) {
            activeExpedition.cancel();
        }
        activeExpedition = expedition;
        activeExpedition.start();
        agent.setState(AgentState.ON_EXPEDITION);
        currentIdleBehavior = IdleBehavior.ON_EXPEDITION;
        wanderTarget = null;
        resetGatherState();
        giveTarget = null;
        followTarget = null;
        navigation.cancel();
    }

    public void cancelExpedition() {
        if (activeExpedition != null) {
            activeExpedition.cancel();
            activeExpedition = null;
            agent.setState(AgentState.IDLE);
            currentIdleBehavior = IdleBehavior.STANDING;
        }
    }

    public ExpeditionController getActiveExpedition() {
        return activeExpedition;
    }

    // --- Task requester management ---

    public void setTaskRequester(UUID requesterId) {
        this.taskRequesterId = requesterId;
        this.approachedRequester = false;
    }

    public void clearTaskRequester() {
        this.taskRequesterId = null;
        this.approachedRequester = false;
    }

    public Location getHomeLocation() {
        return homeLocation.clone();
    }

    public List<ItemStack> getInventory() {
        return Collections.unmodifiableList(inventory);
    }

    // --- Utility ---

    private static int randomRange(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
