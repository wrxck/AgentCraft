package com.agentcraft.agent;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.action.ActionQueue;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.expedition.ExpeditionController;
import com.agentcraft.npc.FakePlayer;

import java.io.File;

public class AIAgent {

    private final AgentCraftPlugin plugin;
    private final FakePlayer npc;
    private final AgentProfile profile;
    private final ActionQueue actionQueue;
    private final BehaviorController behaviorController;
    private final File workingDirectory;
    private AgentState state;
    private String currentTask;

    public AIAgent(AgentCraftPlugin plugin, FakePlayer npc, AgentProfile profile, File workingDirectory) {
        this.plugin = plugin;
        this.npc = npc;
        this.profile = profile;
        this.workingDirectory = workingDirectory;
        this.actionQueue = new ActionQueue(plugin);
        this.behaviorController = new BehaviorController(this);
        this.state = AgentState.IDLE;
        this.currentTask = null;
    }

    public void assignTask(String task) {
        this.currentTask = task;
        this.state = AgentState.THINKING;
    }

    public void clearTask() {
        this.currentTask = null;
    }

    public void startBehavior() {
        behaviorController.start();
    }

    public void stop() {
        if (behaviorController.getActiveExpedition() != null) {
            behaviorController.cancelExpedition();
        }
        behaviorController.stop();
        actionQueue.clear();
        plugin.getAiIntegration().cancelAgent(npc.getName());
        // Unsneak for all viewers
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            npc.sneak(p, false);
        }
        state = AgentState.IDLE;
        currentTask = null;
    }

    public FakePlayer getNpc() { return npc; }
    public AgentProfile getProfile() { return profile; }
    public ActionQueue getActionQueue() { return actionQueue; }
    public BehaviorController getBehaviorController() { return behaviorController; }
    public AgentState getState() { return state; }
    public void setState(AgentState state) { this.state = state; }
    public String getCurrentTask() { return currentTask; }
    public AgentCraftPlugin getPlugin() { return plugin; }
    public File getWorkingDirectory() { return workingDirectory; }

    public ExpeditionController getExpedition() {
        return behaviorController.getActiveExpedition();
    }

    public void cancelExpedition() {
        behaviorController.cancelExpedition();
    }
}
