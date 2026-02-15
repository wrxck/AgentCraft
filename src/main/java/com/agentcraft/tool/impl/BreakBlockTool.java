package com.agentcraft.tool.impl;

import com.agentcraft.action.MineAction;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class BreakBlockTool implements MinecraftTool {

    @Override public String getName() { return "break_block"; }

    @Override public String getDescription() {
        return "Break/mine a block at specific coordinates. Will walk there first if needed.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("x", "integer: X coordinate");
        schema.addProperty("y", "integer: Y coordinate");
        schema.addProperty("z", "integer: Z coordinate");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            return ToolResult.fail("Must provide x, y, z coordinates");
        }

        int x, y, z;
        try {
            x = params.get("x").getAsInt();
            y = params.get("y").getAsInt();
            z = params.get("z").getAsInt();
        } catch (NumberFormatException | ClassCastException e) {
            return ToolResult.fail("Invalid coordinates - x, y, z must be integers");
        }

        Location npcLoc = agent.getNpc().getLocation();
        double dist = Math.sqrt(Math.pow(x - npcLoc.getX(), 2)
                + Math.pow(y - npcLoc.getY(), 2)
                + Math.pow(z - npcLoc.getZ(), 2));

        if (dist > 32) {
            return ToolResult.fail("Too far away (max 32 blocks). Move closer first.");
        }

        World world = npcLoc.getWorld();
        Block block = world.getBlockAt(x, y, z);

        if (!block.getType().isSolid()) {
            return ToolResult.fail("No solid block at " + x + " " + y + " " + z
                    + " (found: " + block.getType().name().toLowerCase().replace('_', ' ') + ")");
        }

        Location blockLoc = new Location(world, x, y, z);
        MineAction mineAction = new MineAction(agent, blockLoc);
        agent.getActionQueue().add(mineAction);
        agent.getActionQueue().start();

        return ToolResult.ok("Mining " + block.getType().name().toLowerCase().replace('_', ' ')
                + " at " + x + " " + y + " " + z);
    }
}
