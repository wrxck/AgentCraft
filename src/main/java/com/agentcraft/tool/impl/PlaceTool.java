package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;

public class PlaceTool implements MinecraftTool {

    @Override public String getName() { return "place"; }

    @Override public String getDescription() {
        return "Place a block from inventory at specific coordinates";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("material", "string: block material to place");
        schema.addProperty("x", "integer: X coordinate");
        schema.addProperty("y", "integer: Y coordinate");
        schema.addProperty("z", "integer: Z coordinate");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        try {
            return run(agent, params);
        } catch (ToolArgs.BadArgument e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private ToolResult run(AIAgent agent, JsonObject params) {
        String material = ToolArgs.optString(params, "material");
        if (material == null || material.isEmpty()) return ToolResult.fail("No material specified");

        int[] coords = ToolArgs.coords(params);
        int x = coords[0];
        int y = coords[1];
        int z = coords[2];

        agent.getBehaviorController().executeAction("place " + material + " " + x + " " + y + " " + z);
        return ToolResult.ok("Placing " + material + " at " + x + " " + y + " " + z);
    }
}
