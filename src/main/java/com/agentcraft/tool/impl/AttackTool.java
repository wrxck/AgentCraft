package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class AttackTool implements MinecraftTool {

    @Override public String getName() { return "attack"; }

    @Override public String getDescription() {
        return "Attack the nearest mob of the specified type";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("mob", "string: mob type (e.g. zombie, skeleton, spider)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String mob = params.has("mob") ? params.get("mob").getAsString() : null;
        if (mob == null || mob.isEmpty()) return ToolResult.fail("No mob type specified");

        agent.getBehaviorController().executeAction("attack " + mob);
        return ToolResult.ok("Attacking " + mob);
    }
}
