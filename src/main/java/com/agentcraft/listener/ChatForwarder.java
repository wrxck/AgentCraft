package com.agentcraft.listener;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.ConversationManager;
import org.bukkit.entity.Player;
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

        // Skip forwarding only when ChatListener will actually dispatch this
        // message as a coding task. The guards here mirror ChatListener's
        // exactly (shared parser + same permission/world/range checks): a
        // mention the task path will NOT act on (e.g. out of range) must still
        // reach the conversation path instead of being silently dropped.
        if (isActionableCodeTask(event.getPlayer(), message)) return;

        // Forward to ConversationManager (thread-safe, schedules to main thread internally)
        conversationManager.onChatMessage(playerName, message);
    }

    private boolean isActionableCodeTask(Player player, String message) {
        if (!player.hasPermission("agentcraft.use")) return false;

        for (AIAgent agent : AgentManager.getInstance().getAllAgents()) {
            String agentName = agent.getNpc().getName();
            for (AgentMentionParser.Mention mention
                    : AgentMentionParser.parseAll(message, List.of(agentName))) {
                if (mention.task().isEmpty()) continue;
                if (!agent.getNpc().getLocation().getWorld().equals(player.getWorld())) continue;
                double radius = agent.getPlugin().getConfig().getDouble("chat-radius", 15);
                if (agent.getNpc().getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;
                return true;
            }
        }
        return false;
    }
}
