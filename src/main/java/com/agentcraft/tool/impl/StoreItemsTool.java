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
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;

public class StoreItemsTool implements MinecraftTool {

    @Override public String getName() { return "store_items"; }

    @Override public String getDescription() {
        return "Store items from your inventory into a nearby chest";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("x", "integer: chest X coordinate");
        schema.addProperty("y", "integer: chest Y coordinate");
        schema.addProperty("z", "integer: chest Z coordinate");
        schema.addProperty("item", "string (optional): material to store (e.g. 'cobblestone'). Omit to store everything.");
        schema.addProperty("count", "integer (optional): max number of items to store. Omit to store all matching items.");
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
        BlockState state = block.getState();

        if (!(state instanceof Chest chest)) {
            return ToolResult.fail("Block at " + x + " " + y + " " + z + " is not a chest (found "
                    + block.getType().name().toLowerCase().replace('_', ' ') + ")");
        }

        Material filterMat = null;
        String itemName = ToolArgs.optString(params, "item");
        if (itemName != null && !itemName.isEmpty()) {
            try {
                filterMat = Material.valueOf(itemName.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                return ToolResult.fail("Unknown material: " + itemName);
            }
        }

        int maxCount = ToolArgs.optInt(params, "count", Integer.MAX_VALUE);
        if (maxCount <= 0) {
            return ToolResult.fail("Count must be positive");
        }

        if (agent.getBehaviorController().getInventory().isEmpty()) {
            return ToolResult.fail("Your inventory is empty");
        }

        Inventory chestInv = chest.getInventory();
        int deposited = ChestTransfer.deposit(agent, chestInv, filterMat, maxCount);

        if (deposited == 0) {
            if (filterMat != null) {
                return ToolResult.fail("No " + filterMat.name().toLowerCase().replace('_', ' ')
                        + " in inventory, or chest is full");
            }
            return ToolResult.fail("Chest is full");
        }

        return ToolResult.ok("Stored " + deposited + " items in chest");
    }
}
