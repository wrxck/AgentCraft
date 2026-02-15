package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gathers multiple blocks of a material type by repeatedly searching, navigating,
 * mining, and collecting until the target count is reached or no more blocks are found.
 * If nothing found nearby, explores outward to find resources.
 */
public class GatherAction extends Action {

    private static final int BREAK_STAGES = 10;
    private static final int TICKS_PER_STAGE = 4;
    private static final int SEARCH_RADIUS = 32;
    private static final int SEARCH_Y_RANGE = 12;
    private static final int MAX_INVENTORY = 64;
    private static final int MAX_EXPLORE_ATTEMPTS = 5;
    private static final int EXPLORE_DISTANCE = 24;

    private enum Phase { SEARCHING, EXPLORING, NAVIGATING, MINING, COLLECTING }

    private final String materialName;
    private final int targetCount;

    private Phase phase = Phase.SEARCHING;
    private int gathered = 0;
    private Location currentBlockLoc;
    private boolean navArrived;
    private boolean navFailed;
    private int breakStage = -1;
    private int stageCooldown;
    private int collectDelay;
    private int localSearchFailures;
    private int exploreAttempts;

    public GatherAction(AIAgent agent, String materialName, int targetCount) {
        super(agent);
        this.materialName = materialName.toUpperCase().replace(' ', '_');
        this.targetCount = targetCount;
    }

    @Override
    public void onStart() {
        phase = Phase.SEARCHING;
        log("Started: looking for " + materialName + " (target=" + targetCount + ")");
    }

    @Override
    public ActionResult tick() {
        return switch (phase) {
            case SEARCHING -> tickSearching();
            case EXPLORING -> tickExploring();
            case NAVIGATING -> tickNavigating();
            case MINING -> tickMining();
            case COLLECTING -> tickCollecting();
        };
    }

    private ActionResult tickSearching() {
        Location blockLoc = findNearestBlock();
        if (blockLoc != null) {
            localSearchFailures = 0;
            currentBlockLoc = blockLoc;
            log("Found " + blockLoc.getBlock().getType().name() + " at "
                    + blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ());

            Location standPos = LocationUtil.findAdjacentStandingPosition(blockLoc);
            if (standPos == null) {
                standPos = LocationUtil.findSafeGround(blockLoc);
            }

            navArrived = false;
            navFailed = false;
            NavigationController nav = agent.getBehaviorController().getNavigation();
            nav.navigateTo(standPos, () -> navArrived = true, () -> navFailed = true);
            phase = Phase.NAVIGATING;
            return ActionResult.CONTINUE;
        }

        // Nothing found nearby — explore outward
        localSearchFailures++;
        if (exploreAttempts >= MAX_EXPLORE_ATTEMPTS) {
            log("Exhausted " + MAX_EXPLORE_ATTEMPTS + " exploration attempts, gathered " + gathered);
            return ActionResult.SUCCESS;
        }

        // Navigate in a random direction to find resources
        exploreAttempts++;
        log("No " + materialName + " nearby, exploring outward (attempt " + exploreAttempts + "/" + MAX_EXPLORE_ATTEMPTS + ")");

        Location npcLoc = agent.getNpc().getLocation();
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double ex = npcLoc.getX() + Math.cos(angle) * EXPLORE_DISTANCE;
        double ez = npcLoc.getZ() + Math.sin(angle) * EXPLORE_DISTANCE;
        Location exploreLoc = new Location(npcLoc.getWorld(), ex, npcLoc.getY(), ez);
        exploreLoc = LocationUtil.findSafeGround(exploreLoc);

        navArrived = false;
        navFailed = false;
        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(exploreLoc, () -> navArrived = true, () -> navFailed = true);
        phase = Phase.EXPLORING;
        return ActionResult.CONTINUE;
    }

    private ActionResult tickExploring() {
        // While navigating to explore location, keep scanning for blocks
        Location blockLoc = findNearestBlock();
        if (blockLoc != null) {
            // Found something while exploring! Switch to gathering it
            agent.getBehaviorController().getNavigation().cancel();
            currentBlockLoc = blockLoc;
            log("Found " + blockLoc.getBlock().getType().name() + " while exploring at "
                    + blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ());

            Location standPos = LocationUtil.findAdjacentStandingPosition(blockLoc);
            if (standPos == null) {
                standPos = LocationUtil.findSafeGround(blockLoc);
            }

            navArrived = false;
            navFailed = false;
            NavigationController nav = agent.getBehaviorController().getNavigation();
            nav.navigateTo(standPos, () -> navArrived = true, () -> navFailed = true);
            phase = Phase.NAVIGATING;
            return ActionResult.CONTINUE;
        }

        if (navArrived || navFailed || !agent.getBehaviorController().getNavigation().isNavigating()) {
            // Arrived at explore location (or failed) — search again
            phase = Phase.SEARCHING;
        }
        return ActionResult.CONTINUE;
    }

    private ActionResult tickNavigating() {
        if (navFailed) {
            log("Navigation failed, searching for another block");
            phase = Phase.SEARCHING;
            return ActionResult.CONTINUE;
        }

        if (navArrived || !agent.getBehaviorController().getNavigation().isNavigating()) {
            double dist = LocationUtil.distanceXZ(agent.getNpc().getLocation(), currentBlockLoc);
            if (dist > 4.5) {
                log("Arrived but too far (dist=" + String.format("%.1f", dist) + "), searching for closer block");
                phase = Phase.SEARCHING;
                return ActionResult.CONTINUE;
            }

            log("Arrived, mining");
            phase = Phase.MINING;
            breakStage = -1;
            stageCooldown = 0;
        }
        return ActionResult.CONTINUE;
    }

    private ActionResult tickMining() {
        Block block = currentBlockLoc.getBlock();
        if (block.getType().isAir()) {
            cancelBreakAnimation();
            phase = Phase.SEARCHING;
            return ActionResult.CONTINUE;
        }

        FakePlayer npc = agent.getNpc();

        // Look at the block
        Location blockCenter = currentBlockLoc.clone().add(0.5, 0.5, 0.5);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, blockCenter);
        }

        if (stageCooldown > 0) {
            stageCooldown--;
            return ActionResult.CONTINUE;
        }

        breakStage++;
        stageCooldown = TICKS_PER_STAGE;

        if (breakStage < BREAK_STAGES) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.swingArm(viewer);
                npc.breakBlockAnimation(viewer, currentBlockLoc, breakStage);
            }
        } else {
            cancelBreakAnimation();
            block.breakNaturally();
            phase = Phase.COLLECTING;
            collectDelay = 5;
        }
        return ActionResult.CONTINUE;
    }

    private ActionResult tickCollecting() {
        if (collectDelay > 0) {
            collectDelay--;
            return ActionResult.CONTINUE;
        }

        Location dropLoc = currentBlockLoc.clone().add(0.5, 0.5, 0.5);
        for (Entity entity : dropLoc.getWorld().getNearbyEntities(dropLoc, 2, 2, 2)) {
            if (entity instanceof Item item) {
                agent.getBehaviorController().addToInventory(item.getItemStack().clone());
                item.remove();
            }
        }

        gathered++;
        log("Gathered " + gathered + "/" + targetCount);

        if (gathered >= targetCount) {
            log("Target reached!");
            return ActionResult.SUCCESS;
        }

        if (agent.getBehaviorController().getInventory().size() >= MAX_INVENTORY) {
            log("Inventory full!");
            return ActionResult.SUCCESS;
        }

        // Reset explore counter on success — we know there are resources in this area
        exploreAttempts = 0;
        phase = Phase.SEARCHING;
        return ActionResult.CONTINUE;
    }

    private Location findNearestBlock() {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX();
        int cy = npcLoc.getBlockY();
        int cz = npcLoc.getBlockZ();

        Block nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_Y_RANGE; y <= SEARCH_Y_RANGE; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    String name = b.getType().name();
                    if (name.contains(materialName) || name.equalsIgnoreCase(materialName)) {
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

    private void cancelBreakAnimation() {
        if (currentBlockLoc != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                agent.getNpc().breakBlockAnimation(viewer, currentBlockLoc, -1);
            }
        }
    }

    @Override
    public void onFail() {
        cancelBreakAnimation();
        agent.getBehaviorController().getNavigation().cancel();
    }

    private void log(String msg) {
        agent.getPlugin().getLogger().info("[Gather] " + agent.getNpc().getName() + ": " + msg);
    }
}
