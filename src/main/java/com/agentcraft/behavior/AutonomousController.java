package com.agentcraft.behavior;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentState;
import com.agentcraft.ai.CompactScanner;
import com.agentcraft.ai.ConversationManager;

import java.util.logging.Logger;

/**
 * Periodic AI think loop that fires when the agent is idle with no players around.
 * Sends environment scan to Claude so the NPC can decide what to do autonomously.
 */
public class AutonomousController {

    private final AIAgent agent;
    private final boolean enabled;
    private final int intervalTicks;
    private final int maxTurns;

    private int ticksUntilNextThink;
    private boolean thinking;

    public AutonomousController(AIAgent agent, boolean enabled, int intervalSeconds, int maxTurns) {
        this.agent = agent;
        this.enabled = enabled;
        this.intervalTicks = intervalSeconds * 20;
        this.maxTurns = maxTurns;
        this.ticksUntilNextThink = intervalTicks;
    }

    /**
     * Called every tick from BehaviorController.tick().
     */
    public void tick() {
        if (!enabled || thinking) return;

        if (--ticksUntilNextThink > 0) return;
        ticksUntilNextThink = intervalTicks;

        // Only think when truly idle
        if (!isAgentIdle()) return;

        triggerAutonomousThink();
    }

    private boolean isAgentIdle() {
        AgentState state = agent.getState();
        if (state != AgentState.IDLE) return false;
        if (agent.getActionQueue().isRunning()) return false;
        BehaviorController bc = agent.getBehaviorController();
        if (bc.getNavigation().isNavigating()) return false;
        if (bc.getActiveExpedition() != null) return false;
        return true;
    }

    private void triggerAutonomousThink() {
        ConversationManager cm = agent.getPlugin().getConversationManager();
        if (cm == null) return;

        thinking = true;
        CompactScanner.ScanResult scanResult = CompactScanner.scan(agent);
        String prompt = "[Autonomous Mode]\n"
                + "No players talking. Look around and decide what to do.\n"
                + "Gather, explore, build, organize, or relax. Use tools.\n\n"
                + scanResult.full;

        agent.getPlugin().getLogger().info("[Autonomous] " + agent.getNpc().getName()
                + " starting autonomous think cycle");

        cm.onAutonomousThink(agent, prompt, maxTurns, () -> {
            thinking = false;
            ticksUntilNextThink = intervalTicks;
        });
    }

    public boolean isThinking() { return thinking; }
    public boolean isEnabled() { return enabled; }
}
