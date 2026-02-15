package com.agentcraft.tool.impl;

import com.agentcraft.action.PlaceAction;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;

public class PlaceBlockTool implements MinecraftTool {

    @Override public String getName() { return "place_block"; }

    @Override public String getDescription() {
        return "Place a block at specific coordinates. Must have the material in inventory or specify a common block.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("material", "string: block material (e.g. cobblestone, oak_planks, torch)");
        schema.addProperty("x", "integer: X coordinate");
        schema.addProperty("y", "integer: Y coordinate");
        schema.addProperty("z", "integer: Z coordinate");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String matName = params.has("material") ? params.get("material").getAsString() : null;
        if (matName == null || matName.isEmpty()) return ToolResult.fail("No material specified");
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            return ToolResult.fail("Must provide x, y, z coordinates");
        }

        int x = params.get("x").getAsInt();
        int y = params.get("y").getAsInt();
        int z = params.get("z").getAsInt();

        Location npcLoc = agent.getNpc().getLocation();
        double dist = Math.sqrt(Math.pow(x - npcLoc.getX(), 2)
                + Math.pow(y - npcLoc.getY(), 2)
                + Math.pow(z - npcLoc.getZ(), 2));

        if (dist > 32) {
            return ToolResult.fail("Too far away (max 32 blocks). Move closer first.");
        }

        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return ToolResult.fail("Unknown material: " + matName);
        }

        if (!material.isBlock()) {
            return ToolResult.fail(matName + " is not a placeable block");
        }

        Location blockLoc = new Location(npcLoc.getWorld(), x, y, z);
        PlaceAction placeAction = new PlaceAction(agent, blockLoc, material);
        agent.getActionQueue().add(placeAction);
        agent.getActionQueue().start();

        return ToolResult.ok("Placing " + matName + " at " + x + " " + y + " " + z);
    }
}
