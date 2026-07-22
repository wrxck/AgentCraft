package com.agentcraft.ai;

/**
 * Extracts the JSON payload of a {@code TOOL_CALL:} marker from an LLM text
 * response. Finds the first complete JSON object after the marker using
 * brace matching.
 */
public final class ToolCallParser {

    private ToolCallParser() {
    }

    /**
     * Result of a marker search: the chat text before the marker and the
     * extracted JSON string (null if no complete JSON object was found).
     */
    public record ParseResult(String chatText, String json) {
    }

    /**
     * Search {@code response} for a TOOL_CALL marker and extract its JSON object.
     *
     * @return a result whose {@code json} is the first complete JSON object after
     *         the marker (or null if none), or null if no marker is present.
     */
    public static ParseResult parse(String response) {
        int toolCallIdx = response.indexOf("TOOL_CALL:");
        if (toolCallIdx < 0) toolCallIdx = response.indexOf("TOOL_CALL :");
        if (toolCallIdx < 0) return null;

        String chatText = response.substring(0, toolCallIdx).trim();
        return new ParseResult(chatText, extractJson(response, toolCallIdx));
    }

    /**
     * Extract the first complete JSON object at or after {@code fromIndex}.
     *
     * @return the JSON object text, or null if there is no complete object.
     */
    public static String extractJson(String text, int fromIndex) {
        int jsonStart = text.indexOf('{', fromIndex);
        if (jsonStart < 0) return null;

        int braceDepth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = jsonStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                // Braces inside string literals must not affect the depth count.
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return text.substring(jsonStart, i + 1);
                }
            }
        }
        return null;
    }
}
