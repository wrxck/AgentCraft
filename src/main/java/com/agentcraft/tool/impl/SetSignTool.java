package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;

public class SetSignTool implements MinecraftTool {

    @Override public String getName() { return "set_sign"; }

    @Override public String getDescription() {
        return "Place a sign at coordinates and write text on it (4 lines max)";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("x", "integer: X coordinate");
        schema.addProperty("y", "integer: Y coordinate");
        schema.addProperty("z", "integer: Z coordinate");
        schema.addProperty("line1", "string: first line of text");
        schema.addProperty("line2", "string: second line (optional)");
        schema.addProperty("line3", "string: third line (optional)");
        schema.addProperty("line4", "string: fourth line (optional)");
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

        if (dist > 16) {
            return ToolResult.fail("Too far away (max 16 blocks)");
        }

        World world = npcLoc.getWorld();
        Block block = world.getBlockAt(x, y, z);

        // Edit an existing sign in place; otherwise only place a new sign into
        // air — never silently destroy whatever block is already there.
        if (!(block.getState() instanceof Sign)) {
            if (!block.getType().isAir()) {
                return ToolResult.fail("There is already a block at " + x + " " + y + " " + z
                        + " (" + block.getType().name().toLowerCase().replace('_', ' ') + ")");
            }
            block.setType(Material.OAK_SIGN);
        }

        if (block.getState() instanceof Sign sign) {
            Side front = Side.FRONT;
            if (params.has("line1")) sign.getSide(front).setLine(0, params.get("line1").getAsString());
            if (params.has("line2")) sign.getSide(front).setLine(1, params.get("line2").getAsString());
            if (params.has("line3")) sign.getSide(front).setLine(2, params.get("line3").getAsString());
            if (params.has("line4")) sign.getSide(front).setLine(3, params.get("line4").getAsString());
            sign.update();
            return ToolResult.ok("Sign placed at " + x + " " + y + " " + z);
        }

        return ToolResult.fail("Failed to create sign");
    }
}
