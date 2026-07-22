package com.agentcraft.ai;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentState;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Converts stream events into NPC visual feedback.
 * Rate-limits chat messages and shows tool use progress.
 */
public class StreamProcessor {

    private static final long CHAT_COOLDOWN_MS = 3000;
    private static final int TOOL_CHAT_INTERVAL = 3;

    private final AIAgent agent;
    private long lastChatTime = 0;
    private int toolUseCount = 0;

    public StreamProcessor(AIAgent agent) {
        this.agent = agent;
    }

    /**
     * Process a stream event and apply visual feedback to the NPC.
     * Must be called on the main server thread.
     */
    public void process(StreamEvent event) {
        if (event == null) return;

        switch (event.getType()) {
            case INIT -> {
                // Agent starts thinking - sneak
                agent.setState(AgentState.THINKING);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    agent.getNpc().sneak(p, true);
                }
            }

            case ASSISTANT_TEXT -> {
                String text = event.getText();
                if (text != null && !text.isBlank()) {
                    chatRateLimited(text);
                }
            }

            case TOOL_USE -> {
                // Transition to WORKING on first tool use
                if (agent.getState() == AgentState.THINKING) {
                    agent.setState(AgentState.WORKING);
                }

                // Swing arm for every tool use
                for (Player p : Bukkit.getOnlinePlayers()) {
                    agent.getNpc().swingArm(p);
                }

                // Show progress every Nth tool use
                toolUseCount++;
                if (toolUseCount % TOOL_CHAT_INTERVAL == 0) {
                    String toolName = event.getToolName();
                    chatImmediate("Using " + toolName + "... (" + toolUseCount + " tools used)");
                }
            }

            case TOOL_RESULT -> {
                // No visual feedback for tool results
            }

            case RESULT -> {
                // Unsneak, announce completion
                for (Player p : Bukkit.getOnlinePlayers()) {
                    agent.getNpc().sneak(p, false);
                }
                agent.getBehaviorController().clearTaskRequester();
                agent.clearTask();
                agent.setState(AgentState.IDLE);

                long durationMs = event.getDurationMs();
                double cost = event.getCostUsd();
                String durationStr = formatDuration(durationMs);
                String costStr = String.format("$%.4f", cost);

                chatImmediate("Done! (" + durationStr + ", " + costStr + ")");
            }

            default -> {}
        }
    }

    private void chatRateLimited(String text) {
        long now = System.currentTimeMillis();
        if (now - lastChatTime < CHAT_COOLDOWN_MS) return;
        lastChatTime = now;

        // Truncate long messages for chat display
        String display = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        broadcastChat(display);
    }

    private void chatImmediate(String text) {
        lastChatTime = System.currentTimeMillis();
        broadcastChat(text);
    }

    private void broadcastChat(String text) {
        String formatted = MessageUtil.agentChat(agent.getNpc().getName(), text);
        Location npcLoc = agent.getNpc().getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location playerLoc = p.getLocation();
            // distanceSquared throws for locations in different worlds
            if (npcLoc.getWorld() == null || !npcLoc.getWorld().equals(playerLoc.getWorld())) {
                continue;
            }
            if (playerLoc.distanceSquared(npcLoc) <= 50 * 50) {
                p.sendMessage(formatted);
            }
        }
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
