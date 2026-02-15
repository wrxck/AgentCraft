package com.agentcraft.tool;

public record ToolResult(boolean success, String message) {

    public static ToolResult ok(String message) {
        return new ToolResult(true, message);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message);
    }
}
