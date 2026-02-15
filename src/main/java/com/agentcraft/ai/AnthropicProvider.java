package com.agentcraft.ai;

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

    // Per-session conversation history: sessionId -> list of {role, content} messages
    private final Map<String, List<JsonObject>> history = new ConcurrentHashMap<>();

    public AnthropicProvider(String apiKey, Logger logger) {
        this.apiKey = apiKey;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
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

                // Add user message to history
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                sessionHistory.add(userMsg);

                // Prune history if too long (keep first message + last N)
                pruneHistory(sessionHistory);

                // Build request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);
                requestBody.addProperty("max_tokens", MAX_TOKENS);
                requestBody.addProperty("stream", true);

                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    requestBody.addProperty("system", systemPrompt);
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
                        + ", session=" + sid + ")");

                HttpResponse<java.io.InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    logger.warning("[AnthropicAPI] HTTP " + response.statusCode() + ": " + errorBody);
                    // Remove the user message we just added since the request failed
                    sessionHistory.remove(sessionHistory.size() - 1);
                    lineCallback.accept("{\"type\":\"result\",\"result\":\"API error (HTTP "
                            + response.statusCode() + ")\",\"session_id\":\"" + escapeJson(sid)
                            + "\",\"total_cost_usd\":0,\"duration_ms\":"
                            + (System.currentTimeMillis() - startTime) + "}");
                    return 1;
                }

                // Parse SSE stream
                StringBuilder fullResponse = new StringBuilder();

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

                            if ("content_block_delta".equals(eventType)) {
                                JsonObject delta = event.getAsJsonObject("delta");
                                if (delta != null && "text_delta".equals(
                                        delta.has("type") ? delta.get("type").getAsString() : "")) {
                                    String text = delta.get("text").getAsString();
                                    fullResponse.append(text);
                                }
                            } else if ("message_stop".equals(eventType)) {
                                break;
                            } else if ("error".equals(eventType)) {
                                JsonObject error = event.getAsJsonObject("error");
                                String errorMsg = error != null && error.has("message")
                                        ? error.get("message").getAsString() : "Unknown error";
                                logger.warning("[AnthropicAPI] Stream error: " + errorMsg);
                                sessionHistory.remove(sessionHistory.size() - 1);
                                lineCallback.accept("{\"type\":\"result\",\"result\":\"Stream error: "
                                        + escapeJson(errorMsg) + "\",\"session_id\":\"" + escapeJson(sid)
                                        + "\",\"total_cost_usd\":0,\"duration_ms\":"
                                        + (System.currentTimeMillis() - startTime) + "}");
                                return 1;
                            }
                        } catch (JsonSyntaxException e) {
                            // Skip malformed SSE data lines
                        }
                    }
                }

                String responseText = fullResponse.toString();
                long durationMs = System.currentTimeMillis() - startTime;

                logger.info("[AnthropicAPI] Response received (" + responseText.length()
                        + " chars, " + durationMs + "ms)");

                // Store assistant response in history
                JsonObject assistantMsg = new JsonObject();
                assistantMsg.addProperty("role", "assistant");
                assistantMsg.addProperty("content", responseText);
                sessionHistory.add(assistantMsg);

                // Emit assistant event (matches CLI format)
                JsonObject assistantEvent = new JsonObject();
                assistantEvent.addProperty("type", "assistant");
                JsonObject message = new JsonObject();
                JsonArray content = new JsonArray();
                JsonObject textBlock = new JsonObject();
                textBlock.addProperty("type", "text");
                textBlock.addProperty("text", responseText);
                content.add(textBlock);
                message.add("content", content);
                assistantEvent.add("message", message);
                lineCallback.accept(assistantEvent.toString());

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

    private void pruneHistory(List<JsonObject> messages) {
        while (messages.size() > MAX_HISTORY_MESSAGES) {
            messages.remove(0);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
