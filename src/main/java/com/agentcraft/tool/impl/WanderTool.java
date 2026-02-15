package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class WanderTool implements MinecraftTool {

    @Override public String getName() { return "wander"; }

    @Override public String getDescription() {
        return "Stroll to a random nearby spot";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject();
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        agent.getBehaviorController().executeAction("wander");
        return ToolResult.ok("Wandering to a nearby spot");
    }
}
