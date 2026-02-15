package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class GoHomeTool implements MinecraftTool {

    @Override public String getName() { return "go_home"; }

    @Override public String getDescription() {
        return "Return to your home location";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject();
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        agent.getBehaviorController().executeAction("home");
        return ToolResult.ok("Heading home");
    }
}
