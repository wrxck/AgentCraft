package com.agentcraft.ai;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.memory.MemoryManager;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ConversationManager {

    private final AgentCraftPlugin plugin;
    private final String model;
    private final int debounceSeconds;

    // Per-NPC conversation state, keyed by agent name (lowercase)
    private final Map<String, NpcSession> sessions = new ConcurrentHashMap<>();

    public ConversationManager(AgentCraftPlugin plugin) {
        this.plugin = plugin;
        this.model = plugin.getConfig().getString("chat-model", "haiku");
        this.debounceSeconds = plugin.getConfig().getInt("chat-debounce-seconds", 2);
    }

    public void onChatMessage(String playerName, String message) {
        for (AIAgent agent : AgentManager.getInstance().getAllAgents()) {
            String agentKey = agent.getNpc().getName().toLowerCase();
            NpcSession session = sessions.computeIfAbsent(agentKey, k -> new NpcSession());

            synchronized (session) {
                session.pendingMessages.add(new ChatPromptBuilder.ChatMessage(playerName, message));
                resetDebounce(agent, session);
            }
        }
    }

    private void resetDebounce(AIAgent agent, NpcSession session) {
        if (session.debounceTask != null) {
            session.debounceTask.cancel();
        }

        // Base debounce + random stagger (0-3 seconds) so NPCs don't all respond at once
        int staggerTicks = ThreadLocalRandom.current().nextInt(0, 60); // 0-3 seconds
        int debounceTicks = debounceSeconds * 20 + staggerTicks;

        session.debounceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processMessages(agent);
        }, debounceTicks);
    }

    private void processMessages(AIAgent agent) {
        String agentKey = agent.getNpc().getName().toLowerCase();
        NpcSession session = sessions.get(agentKey);
        if (session == null) return;

        List<ChatPromptBuilder.ChatMessage> messages;
        synchronized (session) {
            if (session.busy || session.pendingMessages.isEmpty()) return;
            session.busy = true;
            messages = new ArrayList<>(session.pendingMessages);
            session.pendingMessages.clear();
        }

        // Build context on main thread (needs Bukkit API)
        String envContext = EnvironmentScanner.scan(agent);
        String userMessage = ChatPromptBuilder.buildUserMessage(messages);

        plugin.getLogger().info("[Chat] " + agent.getNpc().getName() + " processing "
                + messages.size() + " message(s)");

        // Retrieve relevant memories async, then start chat
        MemoryManager memory = plugin.getMemoryManager();
        String agentName = agent.getNpc().getName();

        // Collect player names for memory storage later
        String primaryPlayer = messages.isEmpty() ? "unknown" : messages.get(0).playerName();

        (memory != null && memory.isAvailable()
                ? memory.retrieve(agentName, userMessage)
                : java.util.concurrent.CompletableFuture.completedFuture("")
        ).handle((memories, throwable) -> {
            String mem = (throwable != null || memories == null) ? "" : memories;
            // Back to main thread to start Claude chat
            Bukkit.getScheduler().runTask(plugin, () -> {
                String systemPrompt = ChatPromptBuilder.buildSystemPrompt(agent, envContext, mem);
                startChat(agent, session, systemPrompt, userMessage, primaryPlayer);
            });
            return null;
        });
    }

    private void startChat(AIAgent agent, NpcSession session, String systemPrompt,
                           String userMessage, String primaryPlayer) {
        ClaudeClient client = new ClaudeClient(plugin);
        StringBuilder responseBuilder = new StringBuilder();

        client.streamChat(userMessage, systemPrompt, agent.getWorkingDirectory(),
                session.sessionId, model, line -> {
                    StreamEvent event = StreamEvent.parse(line);
                    if (event == null) return;

                    // Capture session ID from INIT or RESULT events
                    if (event.getSessionId() != null) {
                        synchronized (session) {
                            session.sessionId = event.getSessionId();
                        }
                    }

                    // Capture text response
                    if (event.getType() == StreamEvent.Type.ASSISTANT_TEXT && event.getText() != null) {
                        responseBuilder.setLength(0);
                        responseBuilder.append(event.getText());
                    }
                    if (event.getType() == StreamEvent.Type.RESULT && event.getText() != null) {
                        responseBuilder.setLength(0);
                        responseBuilder.append(event.getText());
                    }
                }).handle((exitCode, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (throwable != null) {
                        plugin.getLogger().warning("[Chat] " + agent.getNpc().getName()
                                + " chat failed exceptionally: " + throwable.getMessage());
                        return;
                    }
                    String response = responseBuilder.toString().trim();
                    if (!response.isEmpty()) {
                        handleResponse(agent, response, primaryPlayer);
                    } else if (exitCode != null && exitCode != 0) {
                        plugin.getLogger().warning("[Chat] " + agent.getNpc().getName()
                                + " chat failed (exit " + exitCode + ")");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Chat] Error handling response for "
                            + agent.getNpc().getName(), e);
                } finally {
                    synchronized (session) {
                        session.busy = false;
                    }
                    // Check if more messages arrived while we were busy
                    boolean hasMore;
                    synchronized (session) {
                        hasMore = !session.pendingMessages.isEmpty();
                    }
                    if (hasMore) {
                        resetDebounce(agent, session);
                    }
                }
            });
            return null;
        });
    }

    private void handleResponse(AIAgent agent, String response, String playerName) {
        String chatText = response;
        String action = null;

        // Split on action lines (lines starting with "> ")
        String[] lines = response.split("\n");
        StringBuilder textBuilder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("> ")) {
                action = trimmed.substring(2).trim();
            } else if (!trimmed.isEmpty()) {
                if (textBuilder.length() > 0) textBuilder.append(" ");
                textBuilder.append(trimmed);
            }
        }
        chatText = textBuilder.toString().trim();

        // Broadcast NPC chat
        if (!chatText.isEmpty()) {
            String agentName = agent.getNpc().getName();
            String formatted = MessageUtil.agentChat(agentName, chatText);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(formatted);
            }

            // Store conversation as memory
            MemoryManager memory = plugin.getMemoryManager();
            if (memory != null && memory.isAvailable()) {
                String summary = "[" + playerName + "] talked to " + agentName + ". "
                        + agentName + " said: " + chatText;
                memory.store(agentName, playerName, summary);
            }
        }

        // Execute action if present
        if (action != null && !action.isEmpty()) {
            plugin.getLogger().info("[Chat] " + agent.getNpc().getName() + " action: " + action);
            agent.getBehaviorController().executeAction(action);
        }
    }

    /**
     * Clear session state for a specific agent (e.g., on despawn).
     * Prevents stale busy=true sessions from blocking respawned agents.
     */
    public void clearSession(String agentName) {
        NpcSession session = sessions.remove(agentName.toLowerCase());
        if (session != null) {
            synchronized (session) {
                if (session.debounceTask != null) {
                    session.debounceTask.cancel();
                }
            }
        }
    }

    public void shutdown() {
        for (NpcSession session : sessions.values()) {
            if (session.debounceTask != null) {
                session.debounceTask.cancel();
            }
        }
        sessions.clear();
    }

    private static class NpcSession {
        String sessionId;
        boolean busy;
        final List<ChatPromptBuilder.ChatMessage> pendingMessages = new ArrayList<>();
        BukkitTask debounceTask;
    }
}
