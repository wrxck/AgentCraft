package com.agentcraft.ai;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentState;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AIIntegration {

    private final AgentCraftPlugin plugin;
    private final RateLimiter rateLimiter;
    // Package-private for tests.
    final Map<String, ClaudeClient> clients = new ConcurrentHashMap<>();

    public AIIntegration(AgentCraftPlugin plugin) {
        this.plugin = plugin;
        this.rateLimiter = new RateLimiter(
                plugin.getConfig().getInt("rate-limit-tokens", 5),
                plugin.getConfig().getInt("rate-limit-refill-seconds", 60)
        );
    }

    public void submitTask(AIAgent agent, Player requester, String task) {
        // Check if agent is busy first — a busy rejection must not cost a token
        if (agent.getState() == AgentState.THINKING || agent.getState() == AgentState.WORKING) {
            MessageUtil.send(requester, MessageUtil.warning("Agent is busy. Use /agent stop " + agent.getNpc().getName() + " first."));
            return;
        }

        // Rate limit check
        if (!rateLimiter.tryConsume(requester.getUniqueId())) {
            MessageUtil.send(requester, MessageUtil.error("Rate limit reached. Please wait before submitting another task."));
            return;
        }

        agent.assignTask(task);
        agent.getBehaviorController().setTaskRequester(requester.getUniqueId());

        // Build prompt
        String prompt = SystemPromptBuilder.build(agent, task);
        File workDir = agent.getWorkingDirectory();
        int maxTurns = agent.getProfile().getMaxTurns();

        // Create stream processor for visual feedback
        StreamProcessor streamProcessor = new StreamProcessor(agent);

        // Start Claude async
        ClaudeClient client = new ClaudeClient(plugin);
        clients.put(agent.getNpc().getName().toLowerCase(), client);

        UUID requesterId = requester.getUniqueId();

        client.streamQuery(prompt, workDir, maxTurns, line -> {
            // Parse each NDJSON line and dispatch to main thread
            plugin.getLogger().info("[Stream] " + line.substring(0, Math.min(line.length(), 120)));
            StreamEvent event = StreamEvent.parse(line);
            if (event != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Allow RESULT events through even if state is IDLE (for cleanup)
                    if (agent.getCurrentTask() == null && event.getType() != StreamEvent.Type.RESULT) {
                        return;
                    }
                    streamProcessor.process(event);
                });
            }
        }).handle((exitCode, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Only clean up if this client is still the registered one — a
                // newer task may have replaced it while this exit was in flight.
                boolean stillRegistered = clients.remove(
                        agent.getNpc().getName().toLowerCase(), client);

                if (throwable != null) {
                    plugin.getLogger().warning("[AI] Task for " + agent.getNpc().getName()
                            + " completed exceptionally: " + throwable.getMessage());
                }

                if (!stillRegistered) {
                    return;
                }

                int code = (exitCode != null) ? exitCode : -1;

                // If agent is still working/thinking (no RESULT event received), clean up
                if (agent.getState() != AgentState.IDLE) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        agent.getNpc().sneak(p, false);
                    }
                    agent.setState(AgentState.IDLE);

                    if (code != 0) {
                        Player p = Bukkit.getPlayer(requesterId);
                        if (p != null) {
                            MessageUtil.send(p, MessageUtil.error(
                                    agent.getNpc().getName() + "'s task failed (exit code " + code + ")."));
                        }
                    }
                }

                agent.getBehaviorController().clearTaskRequester();
                agent.clearTask();
            });
            return null;
        });

        MessageUtil.send(requester, MessageUtil.info("Task assigned to "
                + MessageUtil.highlight(agent.getNpc().getName()) + ": " + task));
    }

    public void cancelAgent(String agentName) {
        ClaudeClient client = clients.remove(agentName.toLowerCase());
        if (client != null) {
            client.cancel();
        }
    }

    public void shutdown() {
        for (ClaudeClient client : clients.values()) {
            client.cancel();
        }
        clients.clear();
    }
}
