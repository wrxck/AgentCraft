package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class MineTool implements MinecraftTool {

    @Override public String getName() { return "mine"; }

    @Override public String getDescription() {
        return "Mine the nearest block of a material type, or mine a specific block at coordinates";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("target", "string: material name (e.g. stone, iron_ore) or 'x y z' coordinates");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String target = params.has("target") ? params.get("target").getAsString() : null;
        if (target == null || target.isEmpty()) return ToolResult.fail("No target specified");

        agent.getBehaviorController().executeAction("mine " + target);
        return ToolResult.ok("Mining " + target);
    }
}
