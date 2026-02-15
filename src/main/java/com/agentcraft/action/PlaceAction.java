package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class PlaceAction extends Action {

    private static final double PLACE_REACH = 2.5;

    private enum Phase { NAVIGATING, PLACING, DONE }

    private final Location blockLocation;
    private final Material material;
    private Phase phase = Phase.NAVIGATING;
    private boolean navArrived;
    private boolean navFailed;

    public PlaceAction(AIAgent agent, Location blockLocation, Material material) {
        super(agent);
        this.blockLocation = blockLocation;
        this.material = material;
    }

    @Override
    public void onStart() {
        // Check inventory first
        if (!agent.getBehaviorController().hasInInventory(material)) {
            // No block to place
            return;
        }

        Location standPos = LocationUtil.findAdjacentStandingPosition(blockLocation);
        if (standPos == null) {
            standPos = LocationUtil.findSafeGround(blockLocation.clone().add(1, 0, 0));
        }

        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(standPos, () -> navArrived = true, () -> navFailed = true);
    }

    @Override
    public ActionResult tick() {
        return switch (phase) {
            case NAVIGATING -> tickNavigating();
            case PLACING -> tickPlacing();
            case DONE -> ActionResult.SUCCESS;
        };
    }

    private ActionResult tickNavigating() {
        if (navFailed) return ActionResult.FAILED;

        if (!agent.getBehaviorController().hasInInventory(material)) {
            return ActionResult.FAILED;
        }

        if (navArrived || !agent.getBehaviorController().getNavigation().isNavigating()) {
            double dist = LocationUtil.distanceXZ(agent.getNpc().getLocation(), blockLocation);
            if (dist > PLACE_REACH + 1) return ActionResult.FAILED;
            phase = Phase.PLACING;
        }
        return ActionResult.CONTINUE;
    }

    private ActionResult tickPlacing() {
        Block target = blockLocation.getBlock();
        if (target.getType().isSolid()) {
            // Block already occupied
            return ActionResult.FAILED;
        }

        if (!agent.getBehaviorController().removeFromInventory(material)) {
            return ActionResult.FAILED;
        }

        FakePlayer npc = agent.getNpc();
        Location blockCenter = blockLocation.clone().add(0.5, 0.5, 0.5);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, blockCenter);
            npc.swingArm(viewer);
        }

        target.setType(material);
        phase = Phase.DONE;
        return ActionResult.SUCCESS;
    }

    @Override
    public void onFail() {
        agent.getBehaviorController().getNavigation().cancel();
    }
}
