package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class BuildStructureTool implements MinecraftTool {

    private static final int MAX_BLOCKS = 64;

    @Override public String getName() { return "build_structure"; }

    @Override public String getDescription() {
        return "Build a line, wall, or floor of blocks from your inventory. Shapes: 'line' (1D), 'wall' (2D vertical), 'floor' (2D horizontal). Requires blocks in inventory.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("shape", "string: 'line', 'wall', or 'floor'");
        schema.addProperty("material", "string: block material (e.g. cobblestone, oak_planks)");
        schema.addProperty("x1", "integer: start X");
        schema.addProperty("y1", "integer: start Y");
        schema.addProperty("z1", "integer: start Z");
        schema.addProperty("x2", "integer: end X");
        schema.addProperty("y2", "integer: end Y");
        schema.addProperty("z2", "integer: end Z");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String shape = params.has("shape") ? params.get("shape").getAsString() : "line";
        String matName = params.has("material") ? params.get("material").getAsString() : null;
        if (matName == null) return ToolResult.fail("No material specified");

        if (!params.has("x1") || !params.has("y1") || !params.has("z1")
                || !params.has("x2") || !params.has("y2") || !params.has("z2")) {
            return ToolResult.fail("Must provide start (x1,y1,z1) and end (x2,y2,z2) coordinates");
        }

        int x1, y1, z1, x2, y2, z2;
        try {
            x1 = params.get("x1").getAsInt(); y1 = params.get("y1").getAsInt(); z1 = params.get("z1").getAsInt();
            x2 = params.get("x2").getAsInt(); y2 = params.get("y2").getAsInt(); z2 = params.get("z2").getAsInt();
        } catch (NumberFormatException | ClassCastException e) {
            return ToolResult.fail("Invalid coordinates - all values must be integers");
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

        Location npcLoc = agent.getNpc().getLocation();
        double dist1 = Math.sqrt(Math.pow(x1 - npcLoc.getX(), 2)
                + Math.pow(y1 - npcLoc.getY(), 2)
                + Math.pow(z1 - npcLoc.getZ(), 2));
        double dist2 = Math.sqrt(Math.pow(x2 - npcLoc.getX(), 2)
                + Math.pow(y2 - npcLoc.getY(), 2)
                + Math.pow(z2 - npcLoc.getZ(), 2));

        if (dist1 > 32 || dist2 > 32) {
            return ToolResult.fail("Coordinates too far away (max 32 blocks)");
        }

        World world = npcLoc.getWorld();

        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        // Count how many blocks actually need placing (skip blocks that already match)
        int needed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean place = switch (shape.toLowerCase()) {
                        case "floor" -> y == minY;
                        default -> true;
                    };
                    if (place && world.getBlockAt(x, y, z).getType() != material) {
                        needed++;
                    }
                }
            }
        }

        if (needed > MAX_BLOCKS) {
            return ToolResult.fail("Too many blocks (" + needed + "). Max " + MAX_BLOCKS + " per call.");
        }

        // Check inventory has enough blocks
        int available = agent.getBehaviorController().countInInventory(material);
        if (available < needed) {
            return ToolResult.fail("Need " + needed + " " + matName + " but only have " + available + " in inventory");
        }

        // Place blocks, consuming from inventory
        int placed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean place = switch (shape.toLowerCase()) {
                        case "floor" -> y == minY;
                        default -> true;
                    };

                    if (place && world.getBlockAt(x, y, z).getType() != material) {
                        agent.getBehaviorController().removeFromInventory(material, 1);
                        world.getBlockAt(x, y, z).setType(material);
                        placed++;
                    }
                }
            }
        }

        return ToolResult.ok("Placed " + placed + " " + matName + " blocks (" + shape + ") from inventory");
    }
}
