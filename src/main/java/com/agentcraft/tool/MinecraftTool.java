package com.agentcraft.tool;

import com.agentcraft.agent.AIAgent;
import com.google.gson.JsonObject;

public interface MinecraftTool {

    String getName();

    String getDescription();

    JsonObject getParameterSchema();

    ToolResult execute(AIAgent agent, JsonObject params);
}
