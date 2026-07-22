package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.AgentEquipment;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import com.agentcraft.util.MaterialMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central state machine that orchestrates all expedition subsystems.
 * Called every tick from BehaviorController when an expedition is active.
 */
public class ExpeditionController {

    private static final int COMBAT_SCAN_INTERVAL = 20;
    private static final int EATING_DURATION = 20;
    private static final int SURFACE_SCAN_RADIUS = 16;
    private static final int SURFACE_SCAN_Y = 8;
    private static final int UNDERGROUND_SCAN_RADIUS = 8;
    private static final int UNDERGROUND_SCAN_Y = 4;
    private static final int SEARCH_WALK_DISTANCE = 20;
    private static final int BREAK_STAGES = 10;
    private static final int TICKS_PER_STAGE = 3;

    private final AIAgent agent;
    private final UUID ownerUuid;
    private final String targetMaterial;
    private final int targetCount;
    private final MaterialCategory category;
    private final Location homeLocation;

    private ExpeditionState state;
    private ExpeditionState stateBeforeInterrupt;
    private NPCGear gear;
    private MacroNavigator macroNavigator;
    private TunnelMiner tunnelMiner;
    private BranchMiner branchMiner;
    private CaveExplorer caveExplorer;
    private ExpeditionCombatHandler combatHandler;
    private WaypointManager waypointManager;
    private ExpeditionReporter reporter;

    private int gathered;
    private int combatScanCooldown;
    private int eatingTimer;

    // Items actually vacuumed into the NPC inventory during this expedition
    // (material -> count); complete() drops these, and only these, at home.
    private final Map<Material, Integer> collectedItems = new LinkedHashMap<>();

    // Gathering sub-state
    private Location gatherBlockLoc;
    private int breakStage = -1;
    private int stageCooldown;

    // Search sub-state
    private boolean searchNavArrived;
    private boolean searchNavFailed;

    public ExpeditionController(AIAgent agent, Player owner, String targetMaterial, int targetCount) {
        this.agent = agent;
        this.ownerUuid = owner.getUniqueId();
        this.targetMaterial = targetMaterial.toUpperCase().replace(' ', '_');
        this.targetCount = targetCount;
        // Resolve from the normalized name so "ancient debris" categorizes
        // exactly like "ancient_debris".
        this.category = MaterialCategory.resolve(this.targetMaterial);
        this.homeLocation = agent.getBehaviorController().getHomeLocation();
    }

    public void start() {
        gear = new NPCGear();
        macroNavigator = new MacroNavigator(agent);
        combatHandler = new ExpeditionCombatHandler(agent, gear);
        waypointManager = new WaypointManager(agent.getNpc().getName(), agent.getNpc().getLocation());
        reporter = new ExpeditionReporter(agent.getNpc().getName(), ownerUuid);

        gathered = 0;

        // Switch main hand to pickaxe for mining
        switchToPickaxe();

        if (category.isSurface()) {
            // Walk outward in a random direction, scanning while traveling
            Location goal = pickSurfaceGoal();
            macroNavigator.navigateTo(goal);
            state = ExpeditionState.TRAVELING_SURFACE;
        } else {
            // Start digging down with 3x3 tunnel
            tunnelMiner = new TunnelMiner(agent, gear, category.getTargetY());
            tunnelMiner.start();
            state = ExpeditionState.DESCENDING;
        }

        log("Expedition started: " + targetMaterial + " x" + targetCount + " (category=" + category + ")");
    }

    public void tick() {
        if (state == ExpeditionState.COMPLETED || state == ExpeditionState.FAILED) return;

        Location npcLoc = agent.getNpc().getLocation();

        // Waypoints + reporting
        waypointManager.tick(npcLoc);
        reporter.tick(this);

        // Death check
        if (gear.isDead()) {
            handleDeath();
            return;
        }

        // Combat scan
        if (state != ExpeditionState.COMBAT && state != ExpeditionState.FLEEING
                && state != ExpeditionState.EATING) {
            if (--combatScanCooldown <= 0) {
                combatScanCooldown = COMBAT_SCAN_INTERVAL;
                ExpeditionCombatHandler.CombatResult result = combatHandler.scan();
                if (result != ExpeditionCombatHandler.CombatResult.NONE) {
                    stateBeforeInterrupt = state;
                    if (result == ExpeditionCombatHandler.CombatResult.FIGHT) {
                        state = ExpeditionState.COMBAT;
                        switchToSword();
                        if (combatHandler.getCurrentTarget() != null) {
                            reporter.reportCombat(
                                    combatHandler.getCurrentTarget().getType().name().toLowerCase(),
                                    false);
                        }
                    } else {
                        state = ExpeditionState.FLEEING;
                        reporter.reportFleeing(combatHandler.getLastThreatCount());
                    }
                    return;
                }
            }
        }

        // Eating check
        if (state != ExpeditionState.EATING && state != ExpeditionState.COMBAT
                && state != ExpeditionState.FLEEING && gear.shouldEat()) {
            stateBeforeInterrupt = state;
            state = ExpeditionState.EATING;
            eatingTimer = EATING_DURATION;
            reporter.reportLowHealth(gear.getHp());
            return;
        }

        // Delegate to current state
        switch (state) {
            case TRAVELING_SURFACE -> tickTravelingSurface();
            case DESCENDING -> tickDescending();
            case EXPLORING_CAVE -> tickExploringCave();
            case SEARCHING -> tickSearching();
            case BRANCH_MINING -> tickBranchMining();
            case GATHERING -> tickGathering();
            case COMBAT -> tickCombat();
            case FLEEING -> tickFleeing();
            case EATING -> tickEating();
            case RETURNING_HOME -> tickReturningHome();
            default -> {}
        }
    }

    // --- State tick methods ---

    private void tickTravelingSurface() {
        macroNavigator.tick();

        // Scan for surface materials while traveling
        Location found = scanForMaterial(agent.getNpc().getLocation(),
                SURFACE_SCAN_RADIUS, SURFACE_SCAN_Y);
        if (found != null) {
            macroNavigator.cancel();
            gatherBlockLoc = found.clone();
            breakStage = -1;
            stageCooldown = 0;
            reporter.reportFoundMaterial(targetMaterial,
                    found.getBlockX(), found.getBlockY(), found.getBlockZ());
            state = ExpeditionState.GATHERING;
            return;
        }

        // If macro nav finished without finding, pick new direction
        if (macroNavigator.hasArrived() || macroNavigator.hasFailed()) {
            Location newGoal = pickSurfaceGoal();
            macroNavigator.navigateTo(newGoal);
        }
    }

    private void tickDescending() {
        tunnelMiner.tick();

        if (tunnelMiner.isCaveFound()) {
            Location caveLoc = tunnelMiner.getCaveLocation();
            log("Cave found at " + caveLoc.getBlockX() + "," + caveLoc.getBlockY() + "," + caveLoc.getBlockZ());
            caveExplorer = new CaveExplorer(agent, targetMaterial, caveLoc);
            caveExplorer.start();
            state = ExpeditionState.EXPLORING_CAVE;
            return;
        }

        if (tunnelMiner.isArrived()) {
            log("Reached target Y-level " + category.getTargetY() + ", starting branch mining");
            branchMiner = new BranchMiner(agent, gear, targetMaterial,
                    tunnelMiner.getDirX(), tunnelMiner.getDirZ(),
                    tunnelMiner.getPerpX(), tunnelMiner.getPerpZ());
            branchMiner.start();
            state = ExpeditionState.BRANCH_MINING;
        }
    }

    private void tickExploringCave() {
        caveExplorer.tick();

        if (caveExplorer.hasFoundTarget()) {
            Location found = caveExplorer.getTargetLocation();
            reporter.reportFoundMaterial(targetMaterial,
                    found.getBlockX(), found.getBlockY(), found.getBlockZ());
            gatherBlockLoc = found.clone();
            breakStage = -1;
            stageCooldown = 0;
            state = ExpeditionState.GATHERING;
            return;
        }

        if (caveExplorer.isExhausted()) {
            log("Cave exhausted, switching to searching");
            state = ExpeditionState.SEARCHING;
            searchNavArrived = false;
            searchNavFailed = false;
        }
    }

    private void tickBranchMining() {
        branchMiner.tick();

        if (branchMiner.isFoundTarget()) {
            Location found = branchMiner.getTargetLocation();
            reporter.reportFoundMaterial(targetMaterial,
                    found.getBlockX(), found.getBlockY(), found.getBlockZ());
            gatherBlockLoc = found.clone();
            breakStage = -1;
            stageCooldown = 0;
            state = ExpeditionState.GATHERING;
            return;
        }

        if (branchMiner.isExhausted()) {
            log("Branch mining exhausted, switching to searching");
            state = ExpeditionState.SEARCHING;
            searchNavArrived = false;
            searchNavFailed = false;
        }
    }

    private void tickSearching() {
        // Scan around current location
        Location found = scanForMaterial(agent.getNpc().getLocation(),
                UNDERGROUND_SCAN_RADIUS, UNDERGROUND_SCAN_Y);
        if (found != null) {
            agent.getBehaviorController().getNavigation().cancel();
            reporter.reportFoundMaterial(targetMaterial,
                    found.getBlockX(), found.getBlockY(), found.getBlockZ());
            gatherBlockLoc = found.clone();
            breakStage = -1;
            stageCooldown = 0;
            state = ExpeditionState.GATHERING;
            return;
        }

        // Walk in random direction if not already moving
        NavigationController nav = agent.getBehaviorController().getNavigation();
        if (!nav.isNavigating() || searchNavArrived || searchNavFailed) {
            Location npcLoc = agent.getNpc().getLocation();
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double tx = npcLoc.getX() + Math.cos(angle) * SEARCH_WALK_DISTANCE;
            double tz = npcLoc.getZ() + Math.sin(angle) * SEARCH_WALK_DISTANCE;
            Location target = LocationUtil.findSafeGround(
                    new Location(npcLoc.getWorld(), tx, npcLoc.getY(), tz));

            searchNavArrived = false;
            searchNavFailed = false;
            nav.navigateTo(target, () -> searchNavArrived = true, () -> searchNavFailed = true);
        }
    }

    private void tickGathering() {
        if (gatherBlockLoc == null) {
            // Mission may already be fulfilled (e.g. the delayed collection
            // ran while an interrupt was active and deferred the return).
            if (gathered >= targetCount) {
                reporter.reportReturning(gathered, targetMaterial);
                startReturnHome();
                return;
            }
            // Find next block to gather
            Location found = scanForMaterial(agent.getNpc().getLocation(),
                    UNDERGROUND_SCAN_RADIUS, UNDERGROUND_SCAN_Y);
            if (found != null) {
                gatherBlockLoc = found.clone();
                breakStage = -1;
                stageCooldown = 0;
            } else {
                // No more target blocks nearby, resume searching
                if (category.isSurface()) {
                    Location newGoal = pickSurfaceGoal();
                    macroNavigator.navigateTo(newGoal);
                    state = ExpeditionState.TRAVELING_SURFACE;
                } else {
                    state = ExpeditionState.SEARCHING;
                    searchNavArrived = false;
                    searchNavFailed = false;
                }
                return;
            }
        }

        Block block = gatherBlockLoc.getBlock();
        if (block.getType().isAir()) {
            // Block already gone, collect drops and find next
            collectDrops(gatherBlockLoc);
            gatherBlockLoc = null;
            return;
        }

        FakePlayer npc = agent.getNpc();
        double dist = LocationUtil.distanceXZ(npc.getLocation(), gatherBlockLoc);

        // Walk to block if too far
        if (dist > 3.0) {
            NavigationController nav = agent.getBehaviorController().getNavigation();
            if (!nav.isNavigating()) {
                Location standPos = LocationUtil.findAdjacentStandingPosition(gatherBlockLoc);
                if (standPos == null) standPos = LocationUtil.findSafeGround(gatherBlockLoc);
                nav.navigateTo(standPos);
            }
            return;
        }

        // Cancel any nav
        agent.getBehaviorController().getNavigation().cancel();

        // Look at block
        Location blockCenter = gatherBlockLoc.clone().add(0.5, 0.5, 0.5);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, blockCenter);
        }

        if (stageCooldown > 0) {
            stageCooldown--;
            return;
        }

        breakStage++;
        stageCooldown = TICKS_PER_STAGE;

        if (breakStage < BREAK_STAGES) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.swingArm(viewer);
                npc.breakBlockAnimation(viewer, gatherBlockLoc, breakStage);
            }
        } else {
            // Break complete
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.breakBlockAnimation(viewer, gatherBlockLoc, -1);
            }
            block.breakNaturally();
            gear.usePickaxe();

            // Schedule collection
            Location dropLoc = gatherBlockLoc.clone();
            gatherBlockLoc = null;
            breakStage = -1;

            Bukkit.getScheduler().runTaskLater(agent.getPlugin(),
                    () -> finishGatherCollection(dropLoc), 5L);
        }
    }

    /**
     * Runs 5 ticks after a gather block was broken: vacuum the drops and
     * check whether the mission target has been met. Scheduled work must
     * respect the current expedition state: it may run after the expedition
     * was cancelled/failed or while an interrupt (combat/fleeing/eating) is
     * active, and must never stomp those states.
     */
    void finishGatherCollection(Location dropLoc) {
        if (isComplete()) {
            // Expedition was cancelled or failed in the meantime.
            return;
        }

        collectDrops(dropLoc);
        gathered++;
        log("Gathered " + gathered + "/" + targetCount);

        if (gathered >= targetCount && canStartReturningHome()) {
            reporter.reportReturning(gathered, targetMaterial);
            startReturnHome();
        }
        // If an interrupt is active, tickGathering() picks the return up
        // once the interrupt resolves back to GATHERING.
    }

    private boolean canStartReturningHome() {
        return state != ExpeditionState.COMBAT
                && state != ExpeditionState.FLEEING
                && state != ExpeditionState.EATING
                && state != ExpeditionState.RETURNING_HOME;
    }

    private void tickCombat() {
        combatHandler.tick();

        if (combatHandler.isDone()) {
            // The handler nulls its target before we can observe the kill,
            // so it captures the victim's name for us.
            String killedMob = combatHandler.consumeLastKillName();
            if (killedMob != null) {
                reporter.reportCombat(killedMob, true);
            }
            switchToPickaxe();
            state = stateBeforeInterrupt != null ? stateBeforeInterrupt : ExpeditionState.SEARCHING;
            stateBeforeInterrupt = null;
        }
    }

    private void tickFleeing() {
        combatHandler.tick();

        if (combatHandler.isDone()) {
            switchToPickaxe();
            state = stateBeforeInterrupt != null ? stateBeforeInterrupt : ExpeditionState.SEARCHING;
            stateBeforeInterrupt = null;
        }
    }

    private void tickEating() {
        if (eatingTimer > 0) {
            eatingTimer--;
            // Arm swing animation while eating
            if (eatingTimer % 5 == 0) {
                FakePlayer npc = agent.getNpc();
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    npc.swingArm(viewer);
                }
            }
            return;
        }

        gear.tryEat();
        state = stateBeforeInterrupt != null ? stateBeforeInterrupt : ExpeditionState.SEARCHING;
        stateBeforeInterrupt = null;
    }

    private void tickReturningHome() {
        macroNavigator.tick();

        if (macroNavigator.hasArrived()) {
            complete();
            return;
        }

        if (macroNavigator.hasFailed()) {
            // Teleport home as fallback
            teleportNpcTo(homeLocation);
            complete();
        }
    }

    // --- Control methods ---

    public void cancel() {
        reporter.reportCancelled();
        cleanup();
        state = ExpeditionState.FAILED;
    }

    public void cancelAndTeleportHome() {
        reporter.reportCancelled();
        cleanup();
        teleportNpcTo(homeLocation);
        state = ExpeditionState.FAILED;
    }

    private void handleDeath() {
        reporter.reportDeath();
        cleanup();
        // Teleport home
        teleportNpcTo(homeLocation);
        gathered = 0;
        state = ExpeditionState.FAILED;
    }

    /**
     * Teleport the NPC for all viewers AND update its internal position
     * exactly once, even when no players are online to receive packets.
     */
    private void teleportNpcTo(Location destination) {
        FakePlayer npc = agent.getNpc();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.teleport(viewer, destination);
        }
        npc.setLocation(destination);
    }

    private void startReturnHome() {
        macroNavigator.navigateTo(homeLocation);
        state = ExpeditionState.RETURNING_HOME;
    }

    void complete() {
        double distance = waypointManager.getTotalDistanceTraveled();
        reporter.reportComplete(gathered, targetMaterial, distance);

        // Switch back to sword (default held item)
        switchToSword();

        // Drop the items that were actually collected during the expedition:
        // transfer them out of the NPC's tracked inventory instead of
        // manufacturing brand-new stacks (which duplicated every drop that
        // collectDrops() had already vacuumed up).
        for (Map.Entry<Material, Integer> entry : collectedItems.entrySet()) {
            int removed = agent.getBehaviorController()
                    .removeFromInventory(entry.getKey(), entry.getValue());
            if (removed > 0) {
                homeLocation.getWorld().dropItem(homeLocation,
                        new org.bukkit.inventory.ItemStack(entry.getKey(), removed));
            }
        }
        collectedItems.clear();

        cleanup();
        state = ExpeditionState.COMPLETED;
    }

    private void switchToPickaxe() {
        AgentEquipment equip = agent.getNpc().getEquipment();
        if (equip == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().sendMainHand(viewer, equip.getPickaxe());
        }
    }

    private void switchToSword() {
        AgentEquipment equip = agent.getNpc().getEquipment();
        if (equip == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().sendMainHand(viewer, equip.getSword());
        }
    }

    private void cleanup() {
        if (macroNavigator != null) macroNavigator.cancel();
        if (combatHandler != null) combatHandler.cancel();
        if (waypointManager != null) waypointManager.cleanup();
        agent.getBehaviorController().getNavigation().cancel();
    }

    // --- Helpers ---

    Location scanForMaterial(Location center, int radius, int yRange) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Location nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -yRange; y <= yRange; y++) {
                for (int z = -radius; z <= radius; z++) {
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

    private void collectDrops(Location dropLoc) {
        Location center = dropLoc.clone().add(0.5, 0.5, 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 2, 2, 2)) {
            if (entity instanceof Item item) {
                org.bukkit.inventory.ItemStack stack = item.getItemStack().clone();
                agent.getBehaviorController().addToInventory(stack);
                collectedItems.merge(stack.getType(), stack.getAmount(), Integer::sum);
                item.remove();
            }
        }
    }

    private Location pickSurfaceGoal() {
        Location npcLoc = agent.getNpc().getLocation();
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = 200 + ThreadLocalRandom.current().nextInt(300);
        double tx = npcLoc.getX() + Math.cos(angle) * distance;
        double tz = npcLoc.getZ() + Math.sin(angle) * distance;
        return LocationUtil.findSafeGround(
                new Location(npcLoc.getWorld(), tx, npcLoc.getY(), tz));
    }

    private void log(String msg) {
        agent.getPlugin().getLogger().info("[Expedition] " + agent.getNpc().getName() + ": " + msg);
    }

    // --- Getters ---

    public ExpeditionState getState() { return state; }
    public String getTargetMaterial() { return targetMaterial; }
    public int getTargetCount() { return targetCount; }
    public int getGathered() { return gathered; }
    public NPCGear getGear() { return gear; }
    public WaypointManager getWaypointManager() { return waypointManager; }
    public MacroNavigator getMacroNavigator() { return macroNavigator; }
    public MaterialCategory getCategory() { return category; }
    public ExpeditionCombatHandler getCombatHandler() { return combatHandler; }
    public Location getHomeLocation() { return homeLocation; }

    public boolean isComplete() {
        return state == ExpeditionState.COMPLETED || state == ExpeditionState.FAILED;
    }
}
