package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class EatTool implements MinecraftTool {

    @Override public String getName() { return "eat"; }

    @Override public String getDescription() {
        return "Eat food from inventory";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject();
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        agent.getBehaviorController().executeAction("eat");
        return ToolResult.ok("Eating food");
    }
}
