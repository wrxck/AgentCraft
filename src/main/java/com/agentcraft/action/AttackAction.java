package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class AttackAction extends Action {

    private static final double ATTACK_REACH = 2.5;
    private static final double ATTACK_DAMAGE = 4.0; // 2 hearts
    private static final int ATTACK_COOLDOWN_TICKS = 15; // ~0.75 second between hits
    private static final int TIMEOUT_TICKS = 200; // 10 seconds

    private final LivingEntity target;
    private int cooldown;
    private int totalTicks;
    private boolean chasing;

    public AttackAction(AIAgent agent, LivingEntity target) {
        super(agent);
        this.target = target;
    }

    @Override
    public void onStart() {
        // Don't attack players
        if (target instanceof Player) return;

        startChase();
    }

    @Override
    public ActionResult tick() {
        // Safety: never attack players
        if (target instanceof Player) return ActionResult.FAILED;

        totalTicks++;
        if (totalTicks > TIMEOUT_TICKS) return ActionResult.SUCCESS; // timeout, stop trying

        if (target.isDead()) return ActionResult.SUCCESS;

        FakePlayer npc = agent.getNpc();
        double dist = npc.getLocation().distance(target.getLocation());

        // Chase if too far
        if (dist > ATTACK_REACH) {
            if (!chasing) {
                startChase();
            }
            // Update goal if target moved
            NavigationController nav = agent.getBehaviorController().getNavigation();
            nav.updateGoalIfMoved(target.getLocation());
            return ActionResult.CONTINUE;
        }

        // In range - stop navigation and attack
        if (chasing) {
            agent.getBehaviorController().getNavigation().cancel();
            chasing = false;
        }

        // Look at target
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, target.getEyeLocation());
        }

        // Attack on cooldown
        if (cooldown > 0) {
            cooldown--;
            return ActionResult.CONTINUE;
        }

        cooldown = ATTACK_COOLDOWN_TICKS;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.swingArm(viewer);
        }
        target.damage(ATTACK_DAMAGE);

        if (target.isDead()) return ActionResult.SUCCESS;

        return ActionResult.CONTINUE;
    }

    private void startChase() {
        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(target.getLocation());
        chasing = true;
    }

    @Override
    public void onFail() {
        agent.getBehaviorController().getNavigation().cancel();
    }
}
