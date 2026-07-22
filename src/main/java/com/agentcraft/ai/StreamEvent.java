package com.agentcraft.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parses Claude stream-json NDJSON lines.
 *
 * Each line is a JSON object with a "type" field:
 * - "system"    -> INIT (session info)
 * - "assistant" -> content_block with "text" or "tool_use"
 * - "user"      -> TOOL_RESULT (tool execution result)
 * - "result"    -> RESULT (final output with cost/duration)
 */
public class StreamEvent {

    public enum Type {
        INIT,
        ASSISTANT_TEXT,
        TOOL_USE,
        TOOL_RESULT,
        RESULT,
        UNKNOWN
    }

    private final Type type;
    private final String text;
    private final String toolName;
    private final String toolInput;
    private final double costUsd;
    private final long durationMs;
    private final String sessionId;

    private StreamEvent(Type type, String text, String toolName, String toolInput,
                        double costUsd, long durationMs, String sessionId) {
        this.type = type;
        this.text = text;
        this.toolName = toolName;
        this.toolInput = toolInput;
        this.costUsd = costUsd;
        this.durationMs = durationMs;
        this.sessionId = sessionId;
    }

    public static StreamEvent parse(String line) {
        if (line == null || line.isBlank()) return null;

        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            String msgType = json.has("type") ? json.get("type").getAsString() : "";

            return switch (msgType) {
                case "system" -> {
                    String sid = json.has("session_id") ? json.get("session_id").getAsString() : null;
                    yield new StreamEvent(Type.INIT, null, null, null, 0, 0, sid);
                }
                case "assistant" -> parseAssistant(json);
                case "user" -> new StreamEvent(Type.TOOL_RESULT, extractToolResultText(json), null, null, 0, 0, null);
                case "result" -> parseResult(json);
                default -> new StreamEvent(Type.UNKNOWN, null, null, null, 0, 0, null);
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static StreamEvent parseAssistant(JsonObject json) {
        if (!json.has("message")) {
            return new StreamEvent(Type.UNKNOWN, null, null, null, 0, 0, null);
        }

        JsonObject message = json.getAsJsonObject("message");
        if (!message.has("content")) {
            return new StreamEvent(Type.UNKNOWN, null, null, null, 0, 0, null);
        }

        JsonArray content = message.getAsJsonArray("content");
        if (content.isEmpty()) {
            return new StreamEvent(Type.UNKNOWN, null, null, null, 0, 0, null);
        }

        // Scan all content blocks: a message may contain both text and tool_use.
        StringBuilder textBuilder = new StringBuilder();
        String toolName = null;
        String toolInput = null;

        for (JsonElement el : content) {
            if (!el.isJsonObject()) continue;
            JsonObject block = el.getAsJsonObject();
            String blockType = block.has("type") ? block.get("type").getAsString() : "";

            if ("text".equals(blockType)) {
                if (textBuilder.length() > 0) textBuilder.append('\n');
                textBuilder.append(block.has("text") ? block.get("text").getAsString() : "");
            } else if ("tool_use".equals(blockType) && toolName == null) {
                toolName = block.has("name") ? block.get("name").getAsString() : "unknown";
                toolInput = block.has("input") ? normalizeToolInput(block.get("input")) : "";
            }
        }

        String text = textBuilder.length() > 0 ? textBuilder.toString() : null;
        if (toolName != null) {
            // Tool use wins as the event type but any accompanying text is kept.
            return new StreamEvent(Type.TOOL_USE, text, toolName, toolInput, 0, 0, null);
        }
        if (text != null) {
            return new StreamEvent(Type.ASSISTANT_TEXT, text, null, null, 0, 0, null);
        }
        return new StreamEvent(Type.UNKNOWN, null, null, null, 0, 0, null);
    }

    /**
     * Normalize a tool_use "input" element to raw JSON object text. Some
     * producers emit it as a JSON object, others as a string primitive that
     * contains the JSON; toString() on the latter would yield a quoted literal.
     */
    private static String normalizeToolInput(JsonElement input) {
        if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
            return input.getAsString();
        }
        return input.toString();
    }

    private static StreamEvent parseResult(JsonObject json) {
        double cost = 0;
        long duration = 0;

        if (json.has("total_cost_usd")) {
            cost = json.get("total_cost_usd").getAsDouble();
        }
        if (json.has("duration_ms")) {
            duration = json.get("duration_ms").getAsLong();
        }

        String resultText = null;
        if (json.has("result")) {
            resultText = json.get("result").getAsString();
        }

        String sid = json.has("session_id") ? json.get("session_id").getAsString() : null;

        return new StreamEvent(Type.RESULT, resultText, null, null, cost, duration, sid);
    }

    public String getSessionId() { return sessionId; }

    private static String extractToolResultText(JsonObject json) {
        try {
            JsonObject message = json.getAsJsonObject("message");
            JsonArray content = message.getAsJsonArray("content");
            for (JsonElement el : content) {
                JsonObject block = el.getAsJsonObject();
                if ("tool_result".equals(block.get("type").getAsString())) {
                    if (block.has("content")) {
                        return block.get("content").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Type getType() { return type; }
    public String getText() { return text; }
    public String getToolName() { return toolName; }
    public String getToolInput() { return toolInput; }
    public double getCostUsd() { return costUsd; }
    public long getDurationMs() { return durationMs; }
}
