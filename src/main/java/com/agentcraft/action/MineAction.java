package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MineAction extends Action {

    private static final int BREAK_STAGES = 10; // stages 0-9
    private static final int TICKS_PER_STAGE = 4;
    private static final double MINE_REACH = 2.5;

    private enum Phase { NAVIGATING, MINING, COLLECTING, DONE }

    private final Location blockLocation;
    private Phase phase = Phase.NAVIGATING;
    private boolean navArrived;
    private boolean navFailed;
    private int breakStage = -1;
    private int stageCooldown;
    private int collectDelay;

    public MineAction(AIAgent agent, Location blockLocation) {
        super(agent);
        this.blockLocation = blockLocation;
    }

    @Override
    public void onStart() {
        // Find a safe position adjacent to the block
        Location standPos = LocationUtil.findAdjacentStandingPosition(blockLocation);
        if (standPos == null) {
            // Try standing at the block itself (might be above)
            standPos = LocationUtil.findSafeGround(blockLocation);
        }

        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(standPos, () -> navArrived = true, () -> navFailed = true);
    }

    @Override
    public ActionResult tick() {
        return switch (phase) {
            case NAVIGATING -> tickNavigating();
            case MINING -> tickMining();
            case COLLECTING -> tickCollecting();
            case DONE -> ActionResult.SUCCESS;
        };
    }

    private ActionResult tickNavigating() {
        if (navFailed) return ActionResult.FAILED;

        if (navArrived || !agent.getBehaviorController().getNavigation().isNavigating()) {
            // Check if close enough to mine
            double dist = LocationUtil.distanceXZ(agent.getNpc().getLocation(), blockLocation);
            if (dist > MINE_REACH + 1) return ActionResult.FAILED;

            phase = Phase.MINING;
            breakStage = -1;
            stageCooldown = 0;
        }
        return ActionResult.CONTINUE;
    }

    private ActionResult tickMining() {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) {
            // Block already broken
            cancelBreakAnimation();
            phase = Phase.DONE;
            return ActionResult.SUCCESS;
        }

        FakePlayer npc = agent.getNpc();

        // Look at the block
        Location blockCenter = blockLocation.clone().add(0.5, 0.5, 0.5);
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
                npc.breakBlockAnimation(viewer, blockLocation, breakStage);
            }
        } else {
            // Finished mining
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

        // Collect dropped items
        Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
        for (Entity entity : dropLoc.getWorld().getNearbyEntities(dropLoc, 2, 2, 2)) {
            if (entity instanceof Item item) {
                agent.getBehaviorController().addToInventory(item.getItemStack().clone());
                item.remove();
            }
        }

        phase = Phase.DONE;
        return ActionResult.SUCCESS;
    }

    private void cancelBreakAnimation() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().breakBlockAnimation(viewer, blockLocation, -1);
        }
    }

    @Override
    public void onFail() {
        cancelBreakAnimation();
        agent.getBehaviorController().getNavigation().cancel();
    }
}
