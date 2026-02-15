package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class DropTool implements MinecraftTool {

    @Override public String getName() { return "drop"; }

    @Override public String getDescription() {
        return "Drop an item from inventory";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("material", "string: material name to drop (optional, drops first item if omitted)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String material = params.has("material") ? params.get("material").getAsString() : "";
        agent.getBehaviorController().executeAction("drop " + material);
        return ToolResult.ok("Dropping " + (material.isEmpty() ? "item" : material));
    }
}
