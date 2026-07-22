package com.agentcraft.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Shared, forgiving argument parsing for tools. LLM-produced params are often
 * missing, {@code null}, or the wrong type; these helpers turn that into
 * friendly {@link ToolResult#fail} messages (via {@link BadArgument}) instead
 * of raw "Tool error: ..." exceptions.
 */
public final class ToolArgs {

    /** Thrown for a missing or malformed argument; the message is user-facing. */
    public static final class BadArgument extends RuntimeException {
        public BadArgument(String message) {
            super(message);
        }
    }

    private ToolArgs() {
    }

    /** Returns the string value, or {@code null} when absent or JSON null. */
    public static String optString(JsonObject params, String key) {
        JsonElement el = params.get(key);
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) return el.getAsString();
        throw new BadArgument("Invalid value for '" + key + "'");
    }

    /** Returns the int value, or the default when absent or JSON null. */
    public static int optInt(JsonObject params, String key, int defaultValue) {
        JsonElement el = params.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        return toInt(el, key);
    }

    /** Returns the int value, failing with a friendly message when absent. */
    public static int reqInt(JsonObject params, String key) {
        JsonElement el = params.get(key);
        if (el == null || el.isJsonNull()) {
            throw new BadArgument("Missing required parameter '" + key + "'");
        }
        return toInt(el, key);
    }

    /**
     * Returns {x, y, z} as ints. Missing keys keep the tools' historical
     * message; malformed values produce a friendly coordinates message.
     */
    public static int[] coords(JsonObject params) {
        if (!present(params, "x") || !present(params, "y") || !present(params, "z")) {
            throw new BadArgument("Must provide x, y, z coordinates");
        }
        try {
            return new int[] {
                    toInt(params.get("x"), "x"),
                    toInt(params.get("y"), "y"),
                    toInt(params.get("z"), "z")
            };
        } catch (BadArgument e) {
            throw new BadArgument("Invalid coordinates: x, y, z must be whole numbers");
        }
    }

    private static boolean present(JsonObject params, String key) {
        JsonElement el = params.get(key);
        return el != null && !el.isJsonNull();
    }

    private static int toInt(JsonElement el, String key) {
        if (el.isJsonPrimitive()) {
            JsonPrimitive prim = el.getAsJsonPrimitive();
            if (prim.isNumber()) {
                return prim.getAsInt();
            }
            if (prim.isString()) {
                try {
                    return Integer.parseInt(prim.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    // falls through to BadArgument
                }
            }
        }
        throw new BadArgument("Invalid value for '" + key + "': expected a whole number");
    }
}
