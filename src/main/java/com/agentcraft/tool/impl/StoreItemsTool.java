package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            return ToolResult.fail("Must provide x, y, z coordinates of the chest");
        }

        int x = params.get("x").getAsInt();
        int y = params.get("y").getAsInt();
        int z = params.get("z").getAsInt();

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
        if (params.has("item") && !params.get("item").isJsonNull()) {
            String itemName = params.get("item").getAsString();
            try {
                filterMat = Material.valueOf(itemName.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                return ToolResult.fail("Unknown material: " + itemName);
            }
        }

        int maxCount = params.has("count") ? params.get("count").getAsInt() : Integer.MAX_VALUE;

        var npcInventory = agent.getBehaviorController().getInventory();
        if (npcInventory.isEmpty()) {
            return ToolResult.fail("Your inventory is empty");
        }

        Inventory chestInv = chest.getInventory();
        int deposited = 0;

        // Iterate NPC inventory and deposit matching items
        for (ItemStack stack : npcInventory) {
            if (filterMat != null && stack.getType() != filterMat) continue;
            int toStore = Math.min(stack.getAmount(), maxCount - deposited);
            if (toStore <= 0) break;

            ItemStack toAdd = new ItemStack(stack.getType(), toStore);
            Map<Integer, ItemStack> overflow = chestInv.addItem(toAdd);
            int notFit = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            int fit = toStore - notFit;

            if (fit > 0) {
                agent.getBehaviorController().removeFromInventory(stack.getType(), fit);
                deposited += fit;
            }
            if (notFit > 0) break; // chest full
        }

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
