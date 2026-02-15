package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class IdleTool implements MinecraftTool {

    @Override public String getName() { return "idle"; }

    @Override public String getDescription() {
        return "Stop all actions and stand still";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject();
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        agent.getBehaviorController().executeAction("idle");
        return ToolResult.ok("Standing still");
    }
}
