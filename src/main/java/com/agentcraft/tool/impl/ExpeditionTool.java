package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class ExpeditionTool implements MinecraftTool {

    @Override public String getName() { return "expedition"; }

    @Override public String getDescription() {
        return "Go on a long journey to find and gather a material. Digs underground for ores, travels surface for wood/stone.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("material", "string: target material (e.g. diamond_ore, iron_ore, log)");
        schema.addProperty("count", "integer: how many to gather (default 16)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String material = params.has("material") ? params.get("material").getAsString() : null;
        if (material == null || material.isEmpty()) return ToolResult.fail("No material specified");

        int count = params.has("count") ? params.get("count").getAsInt() : 16;
        agent.getBehaviorController().executeAction("expedition " + material + " " + count);
        return ToolResult.ok("Starting expedition for " + count + " " + material);
    }
}
