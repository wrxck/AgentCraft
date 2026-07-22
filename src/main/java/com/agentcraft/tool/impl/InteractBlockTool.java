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
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class InteractBlockTool implements MinecraftTool {

    @Override public String getName() { return "interact_block"; }

    @Override public String getDescription() {
        return "Interact with a block: read chest contents, toggle doors/trapdoors, flip levers/buttons, deposit items into chests";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("x", "integer: X coordinate");
        schema.addProperty("y", "integer: Y coordinate");
        schema.addProperty("z", "integer: Z coordinate");
        schema.addProperty("action", "string: 'read' to inspect contents, 'toggle' to open/close/flip, 'take' to take items from chest, 'deposit' to put items into chest");
        schema.addProperty("item", "string (optional): material filter for deposit/take (e.g. 'cobblestone')");
        schema.addProperty("count", "integer (optional): max number of items to deposit/take");
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
        String action = ToolArgs.optString(params, "action");
        if (action == null || action.isEmpty()) action = "read";

        Location npcLoc = agent.getNpc().getLocation();
        double dist = Math.sqrt(Math.pow(x - npcLoc.getX(), 2)
                + Math.pow(y - npcLoc.getY(), 2)
                + Math.pow(z - npcLoc.getZ(), 2));

        if (dist > 16) {
            return ToolResult.fail("Too far away (max 16 blocks)");
        }

        World world = npcLoc.getWorld();
        Block block = world.getBlockAt(x, y, z);
        Material mat = block.getType();
        BlockState state = block.getState();

        switch (action.toLowerCase()) {
            case "read" -> {
                if (state instanceof Chest chest) {
                    return readChest(chest);
                }
                return ToolResult.ok("Block at " + x + " " + y + " " + z + ": "
                        + mat.name().toLowerCase().replace('_', ' '));
            }
            case "toggle" -> {
                if (block.getBlockData() instanceof Openable openable) {
                    openable.setOpen(!openable.isOpen());
                    block.setBlockData(openable);
                    return ToolResult.ok((openable.isOpen() ? "Opened" : "Closed") + " "
                            + mat.name().toLowerCase().replace('_', ' '));
                }
                if (block.getBlockData() instanceof Powerable powerable) {
                    powerable.setPowered(!powerable.isPowered());
                    block.setBlockData(powerable);
                    return ToolResult.ok((powerable.isPowered() ? "Activated" : "Deactivated") + " "
                            + mat.name().toLowerCase().replace('_', ' '));
                }
                return ToolResult.fail("Block is not toggleable");
            }
            case "take" -> {
                if (state instanceof Chest chest) {
                    FilterAndCount fc = parseFilterAndCount(params);
                    if (fc.error != null) return fc.error;
                    return takeFromChest(agent, chest, fc.filter, fc.maxCount, fc.filterName);
                }
                return ToolResult.fail("Not a container");
            }
            case "deposit" -> {
                if (state instanceof Chest chest) {
                    FilterAndCount fc = parseFilterAndCount(params);
                    if (fc.error != null) return fc.error;
                    return depositIntoChest(agent, chest, fc.filter, fc.maxCount, fc.filterName);
                }
                return ToolResult.fail("Not a container");
            }
            default -> {
                return ToolResult.fail("Unknown action: " + action + ". Use 'read', 'toggle', 'take', or 'deposit'.");
            }
        }
    }

    private static final class FilterAndCount {
        Material filter;
        String filterName;
        int maxCount;
        ToolResult error;
    }

    private FilterAndCount parseFilterAndCount(JsonObject params) {
        FilterAndCount fc = new FilterAndCount();
        fc.filterName = ToolArgs.optString(params, "item");
        if (fc.filterName != null && !fc.filterName.isEmpty()) {
            try {
                fc.filter = Material.valueOf(fc.filterName.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                fc.error = ToolResult.fail("Unknown material: " + fc.filterName);
                return fc;
            }
        }
        fc.maxCount = ToolArgs.optInt(params, "count", Integer.MAX_VALUE);
        if (fc.maxCount <= 0) {
            fc.error = ToolResult.fail("Count must be positive");
        }
        return fc;
    }

    private ToolResult readChest(Chest chest) {
        Inventory inv = chest.getInventory();
        Map<String, Integer> items = new LinkedHashMap<>();

        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                String name = stack.getType().name().toLowerCase().replace('_', ' ');
                items.merge(name, stack.getAmount(), Integer::sum);
            }
        }

        if (items.isEmpty()) {
            return ToolResult.ok("Chest is empty");
        }

        StringBuilder sb = new StringBuilder("Chest contains: ");
        items.forEach((name, count) -> sb.append(count).append("x ").append(name).append(", "));
        sb.setLength(sb.length() - 2);
        return ToolResult.ok(sb.toString());
    }

    private ToolResult takeFromChest(AIAgent agent, Chest chest, Material filter, int maxCount,
                                     String filterName) {
        Inventory inv = chest.getInventory();
        int taken = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (filter != null && stack.getType() != filter) continue;
            int toTake = Math.min(stack.getAmount(), maxCount - taken);
            if (toTake <= 0) break;

            ItemStack takenStack = stack.clone();
            takenStack.setAmount(toTake);
            agent.getBehaviorController().addToInventory(takenStack);

            if (toTake >= stack.getAmount()) {
                inv.setItem(i, null);
            } else {
                ItemStack remainder = stack.clone();
                remainder.setAmount(stack.getAmount() - toTake);
                inv.setItem(i, remainder);
            }
            taken += toTake;
        }

        if (taken == 0) {
            if (filter != null) {
                return ToolResult.fail("No " + filterName + " in that chest");
            }
            return ToolResult.ok("Chest was empty");
        }
        return ToolResult.ok("Took " + taken + " items from chest");
    }

    private ToolResult depositIntoChest(AIAgent agent, Chest chest, Material filter, int maxCount,
                                        String filterName) {
        var npcInventory = agent.getBehaviorController().getInventory();
        if (npcInventory.isEmpty()) {
            return ToolResult.fail("Your inventory is empty");
        }

        boolean hasMatching = filter == null
                || npcInventory.stream().anyMatch(s -> s.getType() == filter);
        if (!hasMatching) {
            return ToolResult.fail("No " + filterName + " in inventory");
        }

        int deposited = ChestTransfer.deposit(agent, chest.getInventory(), filter, maxCount);

        if (deposited == 0) {
            return ToolResult.fail("Chest is full");
        }

        return ToolResult.ok("Deposited " + deposited + " items into chest");
    }
}
