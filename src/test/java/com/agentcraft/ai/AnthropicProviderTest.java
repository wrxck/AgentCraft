package com.agentcraft.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicProviderTest {

    // ---- message builders -------------------------------------------------

    private static JsonObject userText(String text) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", text);
        return msg;
    }

    private static JsonObject assistantText(String text) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "assistant");
        JsonArray content = new JsonArray();
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);
        content.add(block);
        msg.add("content", content);
        return msg;
    }

    private static JsonObject assistantToolUse(String id) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "assistant");
        JsonArray content = new JsonArray();
        JsonObject block = new JsonObject();
        block.addProperty("type", "tool_use");
        block.addProperty("id", id);
        block.addProperty("name", "gather");
        block.add("input", new JsonObject());
        content.add(block);
        msg.add("content", content);
        return msg;
    }

    private static JsonObject userToolResult(String id) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject block = new JsonObject();
        block.addProperty("type", "tool_result");
        block.addProperty("tool_use_id", id);
        block.addProperty("content", "OK");
        content.add(block);
        msg.add("content", content);
        return msg;
    }

    private static boolean hasBlockType(JsonObject msg, String type) {
        JsonElement content = msg.get("content");
        if (content == null || !content.isJsonArray()) return false;
        for (JsonElement el : content.getAsJsonArray()) {
            if (el.isJsonObject() && el.getAsJsonObject().has("type")
                    && type.equals(el.getAsJsonObject().get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    // ---- pruneHistory -----------------------------------------------------

    @Test
    void pruneDropsToolResultPairedWithDroppedToolUse() {
        List<JsonObject> history = new ArrayList<>(List.of(
                assistantToolUse("tu_1"),
                userToolResult("tu_1"),
                userText("hello"),
                assistantText("hi")));

        AnthropicProvider.pruneHistory(history, 3);

        assertFalse(history.isEmpty());
        assertFalse(hasBlockType(history.get(0), "tool_result"),
                "history must never start with an orphaned tool_result");
        assertEquals(2, history.size(), "the tool_use/tool_result pair should be dropped together");
        assertEquals("hello", history.get(0).get("content").getAsString());
    }

    @Test
    void pruneNeverLeavesToolResultFirst() {
        List<JsonObject> history = new ArrayList<>(List.of(
                userText("old"),
                userToolResult("tu_9"),
                userText("hello"),
                assistantText("hi")));

        AnthropicProvider.pruneHistory(history, 3);

        assertFalse(hasBlockType(history.get(0), "tool_result"),
                "a tool_result must not become the first message after pruning");
    }

    @Test
    void pruneKeepsShortHistoryUntouched() {
        List<JsonObject> history = new ArrayList<>(List.of(
                userText("hello"),
                assistantText("hi")));
        AnthropicProvider.pruneHistory(history, 50);
        assertEquals(2, history.size());
    }

    // ---- rollbackAfterError ----------------------------------------------

    @Test
    void rollbackRemovesDanglingTrailingToolUse() {
        // A tool_result user message was just added for a pending tool_use; the
        // request failed. Popping only the user message leaves a trailing
        // assistant tool_use that wedges the session (API 400 on every call).
        List<JsonObject> history = new ArrayList<>(List.of(
                userText("hello"),
                assistantToolUse("tu_1"),
                userToolResult("tu_1")));

        AnthropicProvider.rollbackAfterError(history);

        assertEquals(1, history.size(),
                "the now-dangling assistant tool_use must be removed too");
        assertEquals("hello", history.get(0).get("content").getAsString());
    }

    @Test
    void rollbackKeepsCompletedAssistantTurn() {
        List<JsonObject> history = new ArrayList<>(List.of(
                userText("hello"),
                assistantText("hi"),
                userText("second")));

        AnthropicProvider.rollbackAfterError(history);

        assertEquals(2, history.size());
        assertTrue(hasBlockType(history.get(1), "text"),
                "a plain assistant text message must be kept");
    }

    @Test
    void rollbackOnEmptyHistoryIsSafe() {
        List<JsonObject> history = new ArrayList<>();
        AnthropicProvider.rollbackAfterError(history);
        assertTrue(history.isEmpty());
    }

    // ---- buildToolUseEvent ------------------------------------------------

    @Test
    void toolUseEventEmitsInputAsJsonObject() {
        String line = AnthropicProvider.buildToolUseEvent("gather", "{\"material\":\"log\",\"count\":5}");

        JsonObject event = JsonParser.parseString(line).getAsJsonObject();
        JsonObject block = event.getAsJsonObject("message")
                .getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("tool_use", block.get("type").getAsString());
        assertEquals("gather", block.get("name").getAsString());
        assertTrue(block.get("input").isJsonObject(),
                "tool_use input must be a JSON object, not a string primitive");
        assertEquals("log", block.getAsJsonObject("input").get("material").getAsString());

        // And the full downstream path must work: StreamEvent parse + params extraction
        StreamEvent parsed = StreamEvent.parse(line);
        assertEquals(StreamEvent.Type.TOOL_USE, parsed.getType());
        JsonObject call = JsonParser.parseString(
                "{\"name\":\"" + parsed.getToolName() + "\",\"params\":" + parsed.getToolInput() + "}")
                .getAsJsonObject();
        assertEquals("log", call.getAsJsonObject("params").get("material").getAsString());
    }

    @Test
    void toolUseEventWithEmptyInputFallsBackToEmptyObject() {
        String line = AnthropicProvider.buildToolUseEvent("eat", "");
        JsonObject block = JsonParser.parseString(line).getAsJsonObject()
                .getAsJsonObject("message").getAsJsonArray("content").get(0).getAsJsonObject();
        assertTrue(block.get("input").isJsonObject());
        assertEquals(0, block.getAsJsonObject("input").size());
    }

    @Test
    void toolUseEventWithMalformedInputFallsBackToEmptyObject() {
        String line = AnthropicProvider.buildToolUseEvent("eat", "{not valid json");
        JsonObject block = JsonParser.parseString(line).getAsJsonObject()
                .getAsJsonObject("message").getAsJsonArray("content").get(0).getAsJsonObject();
        assertTrue(block.get("input").isJsonObject(),
                "malformed accumulated input must fall back to an empty object");
    }
}
