package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class FleeTool implements MinecraftTool {

    @Override public String getName() { return "flee"; }

    @Override public String getDescription() {
        return "Run away from the nearest threat";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject(); // no params
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        agent.getBehaviorController().executeAction("flee");
        return ToolResult.ok("Fleeing from danger");
    }
}
