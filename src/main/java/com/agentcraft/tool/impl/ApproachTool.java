package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class ApproachTool implements MinecraftTool {

    @Override public String getName() { return "approach"; }

    @Override public String getDescription() {
        return "Walk closer to a player";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("player", "string: player name to approach");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String name = params.has("player") ? params.get("player").getAsString() : null;
        if (name == null || name.isEmpty()) return ToolResult.fail("No player specified");

        agent.getBehaviorController().executeAction("approach " + name);
        return ToolResult.ok("Approaching " + name);
    }
}
