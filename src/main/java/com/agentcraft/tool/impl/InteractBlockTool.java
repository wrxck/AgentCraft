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
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            return ToolResult.fail("Must provide x, y, z coordinates");
        }

        int x = params.get("x").getAsInt();
        int y = params.get("y").getAsInt();
        int z = params.get("z").getAsInt();
        String action = params.has("action") ? params.get("action").getAsString() : "read";

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
                    return takeFromChest(agent, chest);
                }
                return ToolResult.fail("Not a container");
            }
            case "deposit" -> {
                if (state instanceof Chest chest) {
                    String itemFilter = params.has("item") ? params.get("item").getAsString() : null;
                    int count = params.has("count") ? params.get("count").getAsInt() : Integer.MAX_VALUE;
                    return depositIntoChest(agent, chest, itemFilter, count);
                }
                return ToolResult.fail("Not a container");
            }
            default -> {
                return ToolResult.fail("Unknown action: " + action + ". Use 'read', 'toggle', 'take', or 'deposit'.");
            }
        }
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

    private ToolResult takeFromChest(AIAgent agent, Chest chest) {
        Inventory inv = chest.getInventory();
        int taken = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) {
                agent.getBehaviorController().addToInventory(stack.clone());
                inv.setItem(i, null);
                taken += stack.getAmount();
            }
        }

        if (taken == 0) return ToolResult.ok("Chest was empty");
        return ToolResult.ok("Took " + taken + " items from chest");
    }

    private ToolResult depositIntoChest(AIAgent agent, Chest chest, String itemFilter, int maxCount) {
        Inventory chestInv = chest.getInventory();
        Material filterMat = null;
        if (itemFilter != null && !itemFilter.isEmpty()) {
            try {
                filterMat = Material.valueOf(itemFilter.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                return ToolResult.fail("Unknown material: " + itemFilter);
            }
        }

        var npcInventory = agent.getBehaviorController().getInventory();
        if (npcInventory.isEmpty()) {
            return ToolResult.fail("Your inventory is empty");
        }

        // Collect items to deposit from NPC inventory
        int deposited = 0;
        List<ItemStack> toDeposit = new ArrayList<>();
        for (ItemStack stack : npcInventory) {
            if (filterMat != null && stack.getType() != filterMat) continue;
            int take = Math.min(stack.getAmount(), maxCount - deposited);
            if (take <= 0) break;
            toDeposit.add(new ItemStack(stack.getType(), take));
            deposited += take;
        }

        if (deposited == 0) {
            return ToolResult.fail(filterMat != null
                    ? "No " + itemFilter + " in inventory"
                    : "Nothing to deposit");
        }

        // Try to add items to chest
        int actualDeposited = 0;
        for (ItemStack stack : toDeposit) {
            Map<Integer, ItemStack> overflow = chestInv.addItem(stack);
            int notFit = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            int fit = stack.getAmount() - notFit;
            if (fit > 0) {
                agent.getBehaviorController().removeFromInventory(stack.getType(), fit);
                actualDeposited += fit;
            }
            if (notFit > 0) break; // chest is full
        }

        if (actualDeposited == 0) {
            return ToolResult.fail("Chest is full");
        }

        return ToolResult.ok("Deposited " + actualDeposited + " items into chest");
    }
}
