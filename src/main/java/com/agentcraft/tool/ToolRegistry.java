package com.agentcraft.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, MinecraftTool> tools = new ConcurrentHashMap<>();

    public void register(MinecraftTool tool) {
        tools.put(tool.getName(), tool);
    }

    public MinecraftTool get(String name) {
        return tools.get(name);
    }

    public Collection<MinecraftTool> getAll() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /**
     * Builds a tool catalog string for inclusion in the system prompt.
     */
    public String buildToolDefinitions() {
        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE TOOLS:\n");
        sb.append("When you want to perform a physical action, include a tool call on a new line:\n");
        sb.append("TOOL_CALL: {\"name\": \"tool_name\", \"params\": {\"param1\": \"value1\"}}\n\n");

        for (MinecraftTool tool : tools.values()) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            sb.append("  params: ").append(tool.getParameterSchema().toString()).append("\n");
        }

        return sb.toString();
    }
}
