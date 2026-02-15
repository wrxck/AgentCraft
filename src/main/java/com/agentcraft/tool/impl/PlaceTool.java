package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
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
        String material = params.has("material") ? params.get("material").getAsString() : null;
        if (material == null) return ToolResult.fail("No material specified");
        if (!params.has("x") || !params.has("y") || !params.has("z"))
            return ToolResult.fail("Coordinates x, y, z required");

        int x = params.get("x").getAsInt();
        int y = params.get("y").getAsInt();
        int z = params.get("z").getAsInt();

        agent.getBehaviorController().executeAction("place " + material + " " + x + " " + y + " " + z);
        return ToolResult.ok("Placing " + material + " at " + x + " " + y + " " + z);
    }
}
