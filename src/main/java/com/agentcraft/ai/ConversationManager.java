package com.agentcraft.ai;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.memory.MemoryManager;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolPromptBuilder;
import com.agentcraft.tool.ToolRegistry;
import com.agentcraft.tool.ToolResult;
import com.agentcraft.util.MessageUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ConversationManager {

    private static final int FOLLOW_UP_DELAY_TICKS = 10; // 0.5 seconds

    private final AgentCraftPlugin plugin;
    private final LLMProvider provider;
    private final String model;
    private final int debounceSeconds;
    private final Set<String> allowedPlayers;

    // Per-NPC conversation state, keyed by agent name (lowercase)
    private final Map<String, NpcSession> sessions = new ConcurrentHashMap<>();

    public ConversationManager(AgentCraftPlugin plugin, LLMProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
        this.model = plugin.getConfig().getString("chat-model", "haiku");
        this.debounceSeconds = plugin.getConfig().getInt("chat-debounce-seconds", 2);
        // Case-insensitive whitelist of players allowed to trigger agent conversations
        this.allowedPlayers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.allowedPlayers.addAll(plugin.getConfig().getStringList("chat-players"));
    }

    public void onChatMessage(String playerName, String message) {
        // Only allow whitelisted real players — block agents and unknown senders
        if (!allowedPlayers.contains(playerName)) return;
        if (AgentManager.getInstance().getAgent(playerName) != null) return;

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
        CompactScanner.ScanResult scanResult = CompactScanner.scan(agent);
        session.lastScan = scanResult;
        String userMessage = ChatPromptBuilder.buildUserMessage(messages);

        plugin.getLogger().info("[Chat] " + agent.getNpc().getName() + " processing "
                + messages.size() + " message(s) [env:" + scanResult.full.length() + " chars]");

        // Retrieve relevant memories async, then start chat
        MemoryManager memory = plugin.getMemoryManager();
        String agentName = agent.getNpc().getName();

        // Collect player names for memory storage later
        String primaryPlayer = messages.isEmpty() ? "unknown" : messages.get(0).playerName();

        int maxTurns = agent.getProfile().getMaxTurns();

        (memory != null && memory.isAvailable()
                ? memory.retrieve(agentName, userMessage)
                : java.util.concurrent.CompletableFuture.completedFuture("")
        ).handle((memories, throwable) -> {
            String mem = (throwable != null || memories == null) ? "" : memories;
            // Back to main thread to start Claude chat
            Bukkit.getScheduler().runTask(plugin, () -> {
                String systemPrompt = ToolPromptBuilder.buildCompactSystemPrompt(
                        agent, scanResult.full, mem);
                startChat(agent, session, systemPrompt, userMessage, primaryPlayer, maxTurns);
            });
            return null;
        });
    }

    private void startChat(AIAgent agent, NpcSession session, String systemPrompt,
                           String userMessage, String primaryPlayer, int turnsRemaining) {
        StringBuilder responseBuilder = new StringBuilder();

        provider.streamChat(userMessage, systemPrompt, agent.getWorkingDirectory(),
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
                                + " chat failed: " + throwable.getMessage());
                        releaseBusy(agent, session);
                        return;
                    }
                    String response = responseBuilder.toString().trim();
                    if (!response.isEmpty()) {
                        ToolResult toolResult = handleResponse(agent, response, primaryPlayer);

                        // Multi-turn: if a tool was executed and we have turns left, follow up
                        if (toolResult != null && turnsRemaining > 1) {
                            scheduleFollowUp(agent, session, toolResult, primaryPlayer, turnsRemaining - 1);
                            return; // Don't release busy — follow-up will handle it
                        }
                    } else if (exitCode != null && exitCode != 0) {
                        plugin.getLogger().warning("[Chat] " + agent.getNpc().getName()
                                + " chat failed (exit " + exitCode + ")");
                    }
                    releaseBusy(agent, session);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Chat] Error handling response for "
                            + agent.getNpc().getName(), e);
                    releaseBusy(agent, session);
                }
            });
            return null;
        });
    }

    /**
     * Schedule a follow-up turn after a tool execution.
     * Sends the tool result and updated environment back to Claude so it can decide the next action.
     */
    private void scheduleFollowUp(AIAgent agent, NpcSession session, ToolResult toolResult,
                                   String primaryPlayer, int turnsRemaining) {
        plugin.getLogger().info("[Chat] " + agent.getNpc().getName()
                + " scheduling follow-up (" + turnsRemaining + " turns left)");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                // Build follow-up with delta environment (only changed sections)
                String envDelta;
                if (session.lastScan != null) {
                    envDelta = CompactScanner.scanDelta(agent, session.lastScan);
                    session.lastScan = CompactScanner.scan(agent);
                } else {
                    CompactScanner.ScanResult fresh = CompactScanner.scan(agent);
                    session.lastScan = fresh;
                    envDelta = fresh.full;
                }

                String followUpMessage = "[Tool Result] "
                        + (toolResult.success() ? "OK" : "FAIL") + ": " + toolResult.message() + "\n"
                        + envDelta + "\n"
                        + "Continue with your task. Call another tool if needed, or reply to finish.";

                // Resume session — pass null for systemPrompt (preserved from first turn)
                startChat(agent, session, null, followUpMessage, primaryPlayer, turnsRemaining);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Chat] Follow-up error for "
                        + agent.getNpc().getName(), e);
                releaseBusy(agent, session);
            }
        }, FOLLOW_UP_DELAY_TICKS);
    }

    /**
     * Release the busy flag and check for pending messages.
     */
    private void releaseBusy(AIAgent agent, NpcSession session) {
        boolean hasMore;
        synchronized (session) {
            session.busy = false;
            hasMore = !session.pendingMessages.isEmpty();
        }
        if (hasMore) {
            resetDebounce(agent, session);
        }
    }

    /**
     * Parse and handle a response from Claude.
     * Returns the ToolResult if a tool was called (enables multi-turn chaining), null otherwise.
     */
    private ToolResult handleResponse(AIAgent agent, String response, String playerName) {
        String[] lines = response.split("\n");
        StringBuilder textBuilder = new StringBuilder();
        String legacyAction = null;
        String toolCallJson = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("TOOL_CALL:")) {
                toolCallJson = trimmed.substring("TOOL_CALL:".length()).trim();
            } else if (trimmed.startsWith("> ")) {
                // Legacy action format
                legacyAction = trimmed.substring(2).trim();
            } else if (!trimmed.isEmpty()) {
                if (textBuilder.length() > 0) textBuilder.append(" ");
                textBuilder.append(trimmed);
            }
        }
        String chatText = textBuilder.toString().trim();

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

        // Execute tool call (new structured format)
        if (toolCallJson != null) {
            return executeToolCall(agent, toolCallJson);
        }

        // Legacy fallback: execute > action format
        if (legacyAction != null && !legacyAction.isEmpty()) {
            plugin.getLogger().info("[Chat] " + agent.getNpc().getName() + " action: " + legacyAction);
            agent.getBehaviorController().executeAction(legacyAction);
            return ToolResult.ok("Legacy action: " + legacyAction);
        }

        return null;
    }

    private ToolResult executeToolCall(AIAgent agent, String json) {
        JsonObject call;
        try {
            call = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("[Tool] Failed to parse tool call JSON: " + json
                    + " - " + e.getMessage());
            // Try to salvage as legacy action
            String fallback = json.replaceAll("[{}\"]", "").trim();
            if (!fallback.isEmpty()) {
                plugin.getLogger().info("[Tool] Falling back to legacy action: " + fallback);
                agent.getBehaviorController().executeAction(fallback);
                return ToolResult.ok("Legacy fallback: " + fallback);
            }
            return ToolResult.fail("Invalid JSON: " + e.getMessage());
        }

        if (!call.has("name") || call.get("name").isJsonNull()) {
            plugin.getLogger().warning("[Tool] Missing 'name' field in tool call: " + json);
            return ToolResult.fail("Missing tool name");
        }
        String toolName = call.get("name").getAsString();
        JsonObject params = call.has("params") ? call.getAsJsonObject("params") : new JsonObject();

        ToolRegistry registry = plugin.getToolRegistry();
        if (registry == null) {
            plugin.getLogger().warning("[Tool] No tool registry available");
            return ToolResult.fail("No tool registry");
        }

        MinecraftTool tool = registry.get(toolName);
        if (tool == null) {
            plugin.getLogger().warning("[Tool] Unknown tool: " + toolName);
            return ToolResult.fail("Unknown tool: " + toolName);
        }

        try {
            ToolResult result = tool.execute(agent, params);
            plugin.getLogger().info("[Tool] " + agent.getNpc().getName()
                    + " -> " + toolName + ": " + (result.success() ? "OK" : "FAIL")
                    + " - " + result.message());
            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Tool] " + toolName + " threw exception: " + e.getMessage());
            return ToolResult.fail("Tool error: " + e.getMessage());
        }
    }

    /**
     * Trigger an autonomous think cycle for an NPC.
     * Reuses the existing session/busy mechanism, memory retrieval, tool execution, and multi-turn chain.
     */
    public void onAutonomousThink(com.agentcraft.agent.AIAgent agent, String prompt,
                                   int maxTurns, Runnable onComplete) {
        String agentKey = agent.getNpc().getName().toLowerCase();
        NpcSession session = sessions.computeIfAbsent(agentKey, k -> new NpcSession());

        synchronized (session) {
            if (session.busy) {
                // Already processing — skip this cycle
                onComplete.run();
                return;
            }
            session.busy = true;
        }

        String agentName = agent.getNpc().getName();

        // Retrieve memories async, then start chat
        MemoryManager memory = plugin.getMemoryManager();
        (memory != null && memory.isAvailable()
                ? memory.retrieve(agentName, prompt)
                : java.util.concurrent.CompletableFuture.completedFuture("")
        ).handle((memories, throwable) -> {
            String mem = (throwable != null || memories == null) ? "" : memories;
            Bukkit.getScheduler().runTask(plugin, () -> {
                String systemPrompt = ToolPromptBuilder.buildCompactSystemPrompt(
                        agent, prompt, mem);

                plugin.getLogger().info("[Chat] " + agentName + " processing autonomous think");

                // Wrap onComplete to run after the full chain completes
                Runnable wrappedComplete = () -> {
                    releaseBusy(agent, session);
                    onComplete.run();
                };

                startAutonomousChat(agent, session, systemPrompt, prompt,
                        agentName, maxTurns, wrappedComplete);
            });
            return null;
        });
    }

    private void startAutonomousChat(com.agentcraft.agent.AIAgent agent, NpcSession session,
                                      String systemPrompt, String userMessage,
                                      String agentName, int turnsRemaining, Runnable onComplete) {
        StringBuilder responseBuilder = new StringBuilder();

        provider.streamChat(userMessage, systemPrompt, agent.getWorkingDirectory(),
                session.sessionId, model, line -> {
                    StreamEvent event = StreamEvent.parse(line);
                    if (event == null) return;
                    if (event.getSessionId() != null) {
                        synchronized (session) {
                            session.sessionId = event.getSessionId();
                        }
                    }
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
                        plugin.getLogger().warning("[Chat] " + agentName
                                + " autonomous chat failed: " + throwable.getMessage());
                        onComplete.run();
                        return;
                    }
                    String response = responseBuilder.toString().trim();
                    if (!response.isEmpty()) {
                        ToolResult toolResult = handleResponse(agent, response, "autonomous");
                        if (toolResult != null && turnsRemaining > 1) {
                            // Follow up with delta environment
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                try {
                                    String envDelta;
                                    if (session.lastScan != null) {
                                        envDelta = CompactScanner.scanDelta(agent, session.lastScan);
                                        session.lastScan = CompactScanner.scan(agent);
                                    } else {
                                        CompactScanner.ScanResult fresh = CompactScanner.scan(agent);
                                        session.lastScan = fresh;
                                        envDelta = fresh.full;
                                    }

                                    String followUp = "[Tool Result] "
                                            + (toolResult.success() ? "OK" : "FAIL") + ": "
                                            + toolResult.message() + "\n"
                                            + envDelta + "\n"
                                            + "Continue with your task. Call another tool if needed, or reply to finish.";
                                    startAutonomousChat(agent, session, null, followUp,
                                            agentName, turnsRemaining - 1, onComplete);
                                } catch (Exception e) {
                                    plugin.getLogger().log(Level.WARNING,
                                            "[Chat] Autonomous follow-up error for " + agentName, e);
                                    onComplete.run();
                                }
                            }, FOLLOW_UP_DELAY_TICKS);
                            return;
                        }
                    }
                    onComplete.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Chat] Error handling autonomous response for " + agentName, e);
                    onComplete.run();
                }
            });
            return null;
        });
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
        for (NpcSession session : new ArrayList<>(sessions.values())) {
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
        CompactScanner.ScanResult lastScan;
    }
}
