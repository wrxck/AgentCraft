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
}
