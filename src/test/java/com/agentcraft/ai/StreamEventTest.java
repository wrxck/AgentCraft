package com.agentcraft.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamEventTest {

    @Test
    void parsesTextOnlyAssistantMessage() {
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"Hello there!\"}]}}");
        assertEquals(StreamEvent.Type.ASSISTANT_TEXT, event.getType());
        assertEquals("Hello there!", event.getText());
    }

    @Test
    void parsesToolUseOnlyAssistantMessage() {
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":"
                        + "[{\"type\":\"tool_use\",\"name\":\"gather\","
                        + "\"input\":{\"material\":\"log\",\"count\":5}}]}}");
        assertEquals(StreamEvent.Type.TOOL_USE, event.getType());
        assertEquals("gather", event.getToolName());
        JsonObject input = JsonParser.parseString(event.getToolInput()).getAsJsonObject();
        assertEquals("log", input.get("material").getAsString());
        assertEquals(5, input.get("count").getAsInt());
    }

    @Test
    void mixedTextAndToolUseKeepsBoth() {
        // Realistic Claude CLI stream-json line: assistant message with a text
        // block followed by a tool_use block. The text must not be lost.
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":["
                        + "{\"type\":\"text\",\"text\":\"Sure, on it!\"},"
                        + "{\"type\":\"tool_use\",\"name\":\"gather\","
                        + "\"input\":{\"material\":\"log\"}}]}}");
        assertEquals(StreamEvent.Type.TOOL_USE, event.getType());
        assertEquals("gather", event.getToolName());
        assertEquals("Sure, on it!", event.getText(),
                "text block preceding a tool_use block must be captured");
        JsonObject input = JsonParser.parseString(event.getToolInput()).getAsJsonObject();
        assertEquals("log", input.get("material").getAsString());
    }

    @Test
    void stringPrimitiveToolInputIsNormalizedToRawJson() {
        // The Anthropic API provider path historically emitted "input" as a JSON
        // string primitive. toolInput must be the raw JSON object text either way.
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":"
                        + "[{\"type\":\"tool_use\",\"name\":\"gather\","
                        + "\"input\":\"{\\\"material\\\":\\\"log\\\"}\"}]}}");
        assertEquals(StreamEvent.Type.TOOL_USE, event.getType());
        JsonObject input = JsonParser.parseString(event.getToolInput()).getAsJsonObject();
        assertEquals("log", input.get("material").getAsString(),
                "string-primitive input must be unwrapped to the raw JSON object text");
    }

    @Test
    void parsesResultLine() {
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"result\",\"result\":\"done\",\"session_id\":\"abc-123\","
                        + "\"total_cost_usd\":0.0125,\"duration_ms\":4321}");
        assertEquals(StreamEvent.Type.RESULT, event.getType());
        assertEquals("done", event.getText());
        assertEquals("abc-123", event.getSessionId());
        assertEquals(0.0125, event.getCostUsd(), 1e-9);
        assertEquals(4321, event.getDurationMs());
    }

    @Test
    void parsesSystemInitLine() {
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"sess-1\"}");
        assertEquals(StreamEvent.Type.INIT, event.getType());
        assertEquals("sess-1", event.getSessionId());
    }

    @Test
    void malformedLineReturnsNull() {
        assertNull(StreamEvent.parse("this is not json"));
        assertNull(StreamEvent.parse(""));
        assertNull(StreamEvent.parse(null));
    }
}
