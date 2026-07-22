package com.agentcraft.ai;

import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolRegistry;
import com.google.gson.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM provider using the Anthropic Messages API with SSE streaming.
 * Emits synthetic NDJSON lines compatible with {@link StreamEvent#parse(String)}.
 */
public class AnthropicProvider implements LLMProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_HISTORY_MESSAGES = 50;

    private static final Map<String, String> MODEL_MAP = Map.of(
            "haiku", "claude-haiku-4-5-20251001",
            "sonnet", "claude-sonnet-4-5-20250929",
            "opus", "claude-opus-4-6"
    );

    private final String apiKey;
    private final Logger logger;
    private final HttpClient httpClient;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final JsonArray toolsArray;

    // Per-session conversation history: sessionId -> list of {role, content} messages
    private final Map<String, List<JsonObject>> history = new ConcurrentHashMap<>();

    // Track pending tool_use IDs per session for proper tool_result formatting
    private final Map<String, String> pendingToolUseId = new ConcurrentHashMap<>();

    public AnthropicProvider(String apiKey, Logger logger, ToolRegistry toolRegistry) {
        this.apiKey = apiKey;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.toolsArray = buildToolsArray(toolRegistry);
        logger.info("[AnthropicAPI] Registered " + toolsArray.size() + " tools for native tool calling");
    }

    private static JsonArray buildToolsArray(ToolRegistry registry) {
        JsonArray tools = new JsonArray();
        for (MinecraftTool tool : registry.getAll()) {
            JsonObject toolDef = new JsonObject();
            toolDef.addProperty("name", tool.getName());
            toolDef.addProperty("description", tool.getDescription());

            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");
            JsonObject properties = new JsonObject();
            JsonArray required = new JsonArray();

            JsonObject paramSchema = tool.getParameterSchema();
            for (String key : paramSchema.keySet()) {
                String desc = paramSchema.get(key).getAsString();
                JsonObject prop = new JsonObject();
                int colonIdx = desc.indexOf(':');
                if (colonIdx > 0) {
                    String typeStr = desc.substring(0, colonIdx).trim().toLowerCase();
                    String description = desc.substring(colonIdx + 1).trim();
                    if (typeStr.contains("integer") || typeStr.contains("int")) {
                        prop.addProperty("type", "integer");
                    } else if (typeStr.contains("number") || typeStr.contains("float")) {
                        prop.addProperty("type", "number");
                    } else {
                        prop.addProperty("type", "string");
                    }
                    prop.addProperty("description", description);
                } else {
                    prop.addProperty("type", "string");
                    prop.addProperty("description", desc);
                }
                properties.add(key, prop);
                // Params with "default" or "optional" in description are not required
                if (!desc.toLowerCase().contains("default") && !desc.toLowerCase().contains("optional")) {
                    required.add(key);
                }
            }

            inputSchema.add("properties", properties);
            if (required.size() > 0) {
                inputSchema.add("required", required);
            }
            toolDef.add("input_schema", inputSchema);
            tools.add(toolDef);
        }
        return tools;
    }

    @Override
    public CompletableFuture<Integer> streamChat(String prompt, String systemPrompt, File workingDir,
                                                  String sessionId, String model,
                                                  Consumer<String> lineCallback) {
        return CompletableFuture.supplyAsync(() -> {
            cancelled.set(false);
            long startTime = System.currentTimeMillis();

            try {
                // Resolve or create session
                String sid = (sessionId != null && !sessionId.isEmpty())
                        ? sessionId
                        : UUID.randomUUID().toString();

                // Emit init event with session ID
                lineCallback.accept("{\"type\":\"system\",\"session_id\":\"" + escapeJson(sid) + "\"}");

                // Resolve model ID
                String modelId = MODEL_MAP.getOrDefault(model, model);

                // Build messages array from history + new user message
                List<JsonObject> sessionHistory = history.computeIfAbsent(sid, k -> new ArrayList<>());

                // Check if previous turn had a tool_use — format this message as tool_result
                String pendingId = pendingToolUseId.remove(sid);
                if (pendingId != null) {
                    JsonObject userMsg = new JsonObject();
                    userMsg.addProperty("role", "user");
                    JsonArray content = new JsonArray();
                    JsonObject toolResult = new JsonObject();
                    toolResult.addProperty("type", "tool_result");
                    toolResult.addProperty("tool_use_id", pendingId);
                    toolResult.addProperty("content", prompt);
                    content.add(toolResult);
                    userMsg.add("content", content);
                    sessionHistory.add(userMsg);
                } else {
                    JsonObject userMsg = new JsonObject();
                    userMsg.addProperty("role", "user");
                    userMsg.addProperty("content", prompt);
                    sessionHistory.add(userMsg);
                }

                // Prune history if too long (keep first message + last N)
                pruneHistory(sessionHistory, MAX_HISTORY_MESSAGES);

                // Build request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);
                requestBody.addProperty("max_tokens", MAX_TOKENS);
                requestBody.addProperty("stream", true);

                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    requestBody.addProperty("system", systemPrompt);
                }

                // Add native tools
                if (toolsArray.size() > 0) {
                    requestBody.add("tools", toolsArray);
                }

                JsonArray messagesArray = new JsonArray();
                for (JsonObject msg : sessionHistory) {
                    messagesArray.add(msg);
                }
                requestBody.add("messages", messagesArray);

                String body = requestBody.toString();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", API_VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .timeout(Duration.ofMinutes(5))
                        .build();

                logger.info("[AnthropicAPI] Sending request (model=" + modelId
                        + ", messages=" + sessionHistory.size()
                        + ", tools=" + toolsArray.size()
                        + ", session=" + sid + ")");

                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String errorBody;
                    try (java.io.InputStream errorStream = response.body()) {
                        errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    logger.warning("[AnthropicAPI] HTTP " + response.statusCode() + ": " + errorBody);
                    rollbackAfterError(sessionHistory);
                    lineCallback.accept("{\"type\":\"result\",\"result\":\"API error (HTTP "
                            + response.statusCode() + ")\",\"session_id\":\"" + escapeJson(sid)
                            + "\",\"total_cost_usd\":0,\"duration_ms\":"
                            + (System.currentTimeMillis() - startTime) + "}");
                    return 1;
                }

                // Parse SSE stream — handle both text and tool_use content blocks
                StringBuilder fullResponse = new StringBuilder();
                String toolUseId = null;
                String toolUseName = null;
                StringBuilder toolUseInput = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled.get()) {
                            logger.info("[AnthropicAPI] Request cancelled");
                            break;
                        }

                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;

                        try {
                            JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                            String eventType = event.has("type") ? event.get("type").getAsString() : "";

                            switch (eventType) {
                                case "content_block_start" -> {
                                    JsonObject block = event.has("content_block")
                                            ? event.getAsJsonObject("content_block") : null;
                                    if (block != null && "tool_use".equals(
                                            block.has("type") ? block.get("type").getAsString() : "")) {
                                        toolUseId = block.has("id") ? block.get("id").getAsString() : null;
                                        toolUseName = block.has("name") ? block.get("name").getAsString() : null;
                                        toolUseInput.setLength(0);
                                    }
                                }
                                case "content_block_delta" -> {
                                    JsonObject delta = event.has("delta")
                                            ? event.getAsJsonObject("delta") : null;
                                    if (delta != null) {
                                        String deltaType = delta.has("type") ? delta.get("type").getAsString() : "";
                                        if ("text_delta".equals(deltaType)) {
                                            fullResponse.append(delta.get("text").getAsString());
                                        } else if ("input_json_delta".equals(deltaType)) {
                                            toolUseInput.append(delta.get("partial_json").getAsString());
                                        }
                                    }
                                }
                                case "message_stop" -> {}
                                case "error" -> {
                                    JsonObject error = event.getAsJsonObject("error");
                                    String errorMsg = error != null && error.has("message")
                                            ? error.get("message").getAsString() : "Unknown error";
                                    logger.warning("[AnthropicAPI] Stream error: " + errorMsg);
                                    rollbackAfterError(sessionHistory);
                                    lineCallback.accept("{\"type\":\"result\",\"result\":\"Stream error: "
                                            + escapeJson(errorMsg) + "\",\"session_id\":\"" + escapeJson(sid)
                                            + "\",\"total_cost_usd\":0,\"duration_ms\":"
                                            + (System.currentTimeMillis() - startTime) + "}");
                                    return 1;
                                }
                                default -> {}
                            }
                        } catch (JsonSyntaxException e) {
                            // Skip malformed SSE data lines
                        }
                    }
                }

                String responseText = fullResponse.toString();
                long durationMs = System.currentTimeMillis() - startTime;

                logger.info("[AnthropicAPI] Response received (" + responseText.length() + " chars"
                        + (toolUseName != null ? ", tool=" + toolUseName : "") + ", " + durationMs + "ms)");

                // Store assistant response in history with proper content blocks
                JsonObject assistantMsg = new JsonObject();
                assistantMsg.addProperty("role", "assistant");
                JsonArray contentBlocks = new JsonArray();

                if (!responseText.isEmpty()) {
                    JsonObject textBlock = new JsonObject();
                    textBlock.addProperty("type", "text");
                    textBlock.addProperty("text", responseText);
                    contentBlocks.add(textBlock);
                }

                if (toolUseName != null && toolUseId != null) {
                    JsonObject toolBlock = new JsonObject();
                    toolBlock.addProperty("type", "tool_use");
                    toolBlock.addProperty("id", toolUseId);
                    toolBlock.addProperty("name", toolUseName);
                    String inputStr = toolUseInput.toString();
                    try {
                        toolBlock.add("input", inputStr.isEmpty()
                                ? new JsonObject()
                                : JsonParser.parseString(inputStr).getAsJsonObject());
                    } catch (Exception e) {
                        toolBlock.add("input", new JsonObject());
                    }
                    contentBlocks.add(toolBlock);

                    // Store pending ID so next call formats as tool_result
                    pendingToolUseId.put(sid, toolUseId);
                }

                assistantMsg.add("content", contentBlocks);
                sessionHistory.add(assistantMsg);

                // Emit ASSISTANT_TEXT event for chat text
                if (!responseText.isEmpty()) {
                    JsonObject textEvent = new JsonObject();
                    textEvent.addProperty("type", "assistant");
                    JsonObject msg = new JsonObject();
                    JsonArray content = new JsonArray();
                    JsonObject tb = new JsonObject();
                    tb.addProperty("type", "text");
                    tb.addProperty("text", responseText);
                    content.add(tb);
                    msg.add("content", content);
                    textEvent.add("message", msg);
                    lineCallback.accept(textEvent.toString());
                }

                // Emit TOOL_USE event for tool calls
                if (toolUseName != null) {
                    lineCallback.accept(buildToolUseEvent(toolUseName, toolUseInput.toString()));
                }

                // Emit result event
                JsonObject resultEvent = new JsonObject();
                resultEvent.addProperty("type", "result");
                resultEvent.addProperty("result", responseText);
                resultEvent.addProperty("session_id", sid);
                resultEvent.addProperty("total_cost_usd", 0);
                resultEvent.addProperty("duration_ms", durationMs);
                lineCallback.accept(resultEvent.toString());

                return 0;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[AnthropicAPI] Request failed", e);
                return -1;
            }
        });
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Clear history for a session (called when agent despawns).
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            history.remove(sessionId);
        }
    }

    // Package-private for tests.
    static void pruneHistory(List<JsonObject> messages, int maxMessages) {
        while (messages.size() > maxMessages) {
            JsonObject removed = messages.remove(0);
            // Dropping an assistant tool_use must also drop its paired
            // tool_result, or the API rejects the orphan with a 400 forever.
            if (containsBlockType(removed, "tool_use") && !messages.isEmpty()
                    && containsBlockType(messages.get(0), "tool_result")) {
                messages.remove(0);
            }
        }
        // A tool_result must never end up as the first message.
        while (!messages.isEmpty() && containsBlockType(messages.get(0), "tool_result")) {
            messages.remove(0);
        }
    }

    /**
     * Build the synthetic assistant tool_use NDJSON event emitted to the stream
     * callback. Package-private for tests.
     */
    static String buildToolUseEvent(String toolUseName, String inputStr) {
        JsonObject toolEvent = new JsonObject();
        toolEvent.addProperty("type", "assistant");
        JsonObject msg = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject tb = new JsonObject();
        tb.addProperty("type", "tool_use");
        tb.addProperty("name", toolUseName);
        // Input must be a real JSON object: emitting it as a string primitive
        // breaks downstream params parsing (getAsJsonObject on a string).
        JsonObject inputObj;
        try {
            inputObj = (inputStr == null || inputStr.isEmpty())
                    ? new JsonObject()
                    : JsonParser.parseString(inputStr).getAsJsonObject();
        } catch (Exception e) {
            inputObj = new JsonObject();
        }
        tb.add("input", inputObj);
        content.add(tb);
        msg.add("content", content);
        toolEvent.add("message", msg);
        return toolEvent.toString();
    }

    /**
     * Roll back session history after a failed request: removes the just-added
     * user message, plus any assistant tool_use left dangling at the tail —
     * a trailing tool_use without its tool_result wedges the session with a
     * 400 on every subsequent call. Package-private for tests.
     */
    static void rollbackAfterError(List<JsonObject> messages) {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
        while (!messages.isEmpty()) {
            JsonObject last = messages.get(messages.size() - 1);
            boolean assistant = last.has("role") && "assistant".equals(last.get("role").getAsString());
            if (assistant && containsBlockType(last, "tool_use")) {
                messages.remove(messages.size() - 1);
            } else {
                break;
            }
        }
    }

    private static boolean containsBlockType(JsonObject message, String blockType) {
        JsonElement content = message.get("content");
        if (content == null || !content.isJsonArray()) return false;
        for (JsonElement el : content.getAsJsonArray()) {
            if (el.isJsonObject()) {
                JsonElement type = el.getAsJsonObject().get("type");
                if (type != null && blockType.equals(type.getAsString())) return true;
            }
        }
        return false;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
