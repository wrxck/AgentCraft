package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import org.bukkit.Location;

public class MoveAction extends Action {

    private static final double ARRIVAL_THRESHOLD = 1.5;

    private final Location target;
    private boolean navigationStarted;
    private boolean arrived;
    private boolean failed;

    public MoveAction(AIAgent agent, Location target) {
        super(agent);
        this.target = target;
    }

    @Override
    public void onStart() {
        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(target, () -> arrived = true, () -> failed = true);
        navigationStarted = true;
    }

    @Override
    public ActionResult tick() {
        if (!navigationStarted) return ActionResult.FAILED;
        if (arrived) return ActionResult.SUCCESS;
        if (failed) return ActionResult.FAILED;

        // Navigation ticks in BehaviorController, we just check state
        NavigationController nav = agent.getBehaviorController().getNavigation();
        if (!nav.isNavigating() && !arrived) {
            // Navigation finished without callback — check distance
            double dist = agent.getNpc().getLocation().distanceSquared(target);
            return dist < ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD ? ActionResult.SUCCESS : ActionResult.FAILED;
        }

        return ActionResult.CONTINUE;
    }

    @Override
    public void onFail() {
        agent.getBehaviorController().getNavigation().cancel();
    }
}
