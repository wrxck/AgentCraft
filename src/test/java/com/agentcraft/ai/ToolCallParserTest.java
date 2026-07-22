package com.agentcraft.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToolCallParserTest {

    @Test
    void extractsSimpleToolCall() {
        ToolCallParser.ParseResult result = ToolCallParser.parse(
                "Sure! TOOL_CALL:{\"name\":\"gather\",\"params\":{\"material\":\"log\"}}");
        assertEquals("Sure!", result.chatText());
        assertEquals("{\"name\":\"gather\",\"params\":{\"material\":\"log\"}}", result.json());
    }

    @Test
    void extractsNestedObjects() {
        String json = "{\"name\":\"place\",\"params\":{\"pos\":{\"x\":1,\"y\":2,\"z\":3}}}";
        ToolCallParser.ParseResult result = ToolCallParser.parse("TOOL_CALL:" + json);
        assertEquals(json, result.json());
    }

    @Test
    void braceInsideStringDoesNotTruncate() {
        String json = "{\"name\":\"chat\",\"params\":{\"message\":\"smile :-} ok\"}}";
        ToolCallParser.ParseResult result = ToolCallParser.parse("TOOL_CALL:" + json);
        assertEquals(json, result.json(),
                "a '}' inside a JSON string literal must not close the object");
    }

    @Test
    void escapedQuoteInsideStringIsHandled() {
        String json = "{\"name\":\"chat\",\"params\":{\"message\":\"say \\\" and } here\"}}";
        ToolCallParser.ParseResult result = ToolCallParser.parse("TOOL_CALL:" + json);
        assertEquals(json, result.json(),
                "escaped quotes must not end the string literal early");
    }

    @Test
    void missingClosingBraceReturnsNullJson() {
        ToolCallParser.ParseResult result = ToolCallParser.parse(
                "TOOL_CALL:{\"name\":\"gather\",\"params\":{\"material\":\"log\"}");
        assertNull(result.json());
    }

    @Test
    void multipleToolCallsExtractsFirstOnly() {
        ToolCallParser.ParseResult result = ToolCallParser.parse(
                "TOOL_CALL:{\"name\":\"a\",\"params\":{}} and TOOL_CALL:{\"name\":\"b\",\"params\":{}}");
        assertEquals("{\"name\":\"a\",\"params\":{}}", result.json());
    }

    @Test
    void spacedMarkerVariantIsRecognized() {
        ToolCallParser.ParseResult result = ToolCallParser.parse(
                "Okay. TOOL_CALL :{\"name\":\"eat\",\"params\":{}}");
        assertEquals("Okay.", result.chatText());
        assertEquals("{\"name\":\"eat\",\"params\":{}}", result.json());
    }

    @Test
    void noMarkerReturnsNull() {
        assertNull(ToolCallParser.parse("Just a normal chat message"));
    }
}
