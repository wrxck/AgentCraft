package com.agentcraft.listener;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.ConversationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ChatForwarder implements Listener {

    private final Set<String> chatPlayers;
    private final ConversationManager conversationManager;

    public ChatForwarder(List<String> chatPlayers, ConversationManager conversationManager) {
        // Case-insensitive set for whitelist lookup
        this.chatPlayers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.chatPlayers.addAll(chatPlayers);
        this.conversationManager = conversationManager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String playerName = event.getPlayer().getName();

        // Only forward for whitelisted players
        if (!chatPlayers.contains(playerName)) return;

        String message = event.getMessage();

        // Check if this message triggers a coding task for any NPC
        // If so, skip forwarding — let ChatListener handle it
        if (isCodeTask(message)) return;

        // Forward to ConversationManager (thread-safe, schedules to main thread internally)
        conversationManager.onChatMessage(playerName, message);
    }

    private boolean isCodeTask(String message) {
        String lowerMsg = message.toLowerCase();
        for (AIAgent agent : AgentManager.getInstance().getAllAgents()) {
            String agentName = agent.getNpc().getName().toLowerCase();
            if (lowerMsg.contains(agentName)) {
                // Check if there's task text after the agent name
                int nameIdx = lowerMsg.indexOf(agentName);
                String afterName = message.substring(nameIdx + agentName.length()).trim();
                // Strip leading punctuation
                if (!afterName.isEmpty() && (afterName.charAt(0) == ',' || afterName.charAt(0) == ':')) {
                    afterName = afterName.substring(1).trim();
                }
                if (!afterName.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
