package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class CheckBlockTool implements MinecraftTool {

    @Override public String getName() { return "check_block"; }

    @Override public String getDescription() {
        return "Check what type of block is at specific coordinates";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
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
        int[] coords = ToolArgs.coords(params);
        int x = coords[0];
        int y = coords[1];
        int z = coords[2];

        Location npcLoc = agent.getNpc().getLocation();
        double dist = Math.sqrt(Math.pow(x - npcLoc.getX(), 2)
                + Math.pow(y - npcLoc.getY(), 2)
                + Math.pow(z - npcLoc.getZ(), 2));

        if (dist > 64) {
            return ToolResult.fail("Too far away (max 64 blocks). Move closer first.");
        }

        World world = npcLoc.getWorld();
        Block block = world.getBlockAt(x, y, z);
        String name = block.getType().name().toLowerCase().replace('_', ' ');

        return ToolResult.ok("Block at " + x + " " + y + " " + z + ": " + name);
    }
}
