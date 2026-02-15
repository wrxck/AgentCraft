package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class GatherTool implements MinecraftTool {

    @Override public String getName() { return "gather"; }

    @Override public String getDescription() {
        return "Gather multiple blocks of a material type. Use partial names: 'log' for any wood, 'ore' for any ore.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("material", "string: material name (e.g. log, stone, iron_ore)");
        schema.addProperty("count", "integer: number to gather (default 16)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String material = params.has("material") ? params.get("material").getAsString() : null;
        if (material == null || material.isEmpty()) return ToolResult.fail("No material specified");

        int count = params.has("count") ? params.get("count").getAsInt() : 16;
        agent.getBehaviorController().executeAction("gather " + material + " " + count);
        return ToolResult.ok("Gathering " + count + " " + material);
    }
}
