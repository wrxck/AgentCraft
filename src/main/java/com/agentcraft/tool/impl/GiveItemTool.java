package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveItemTool implements MinecraftTool {

    @Override public String getName() { return "give_item"; }

    @Override public String getDescription() {
        return "Drop items from your inventory near a player as a gift";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("player", "string: player name to give items to");
        schema.addProperty("material", "string: material to give (e.g. oak_log, cobblestone). Omit to give everything.");
        schema.addProperty("count", "integer: number of items (default: all matching)");
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
        String playerName = ToolArgs.optString(params, "player");
        if (playerName == null || playerName.isEmpty()) return ToolResult.fail("No player specified");

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) return ToolResult.fail("Player not found: " + playerName);

        if (!target.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
            return ToolResult.fail("Player is in a different world");
        }

        String matName = ToolArgs.optString(params, "material");
        int maxCount = ToolArgs.optInt(params, "count", Integer.MAX_VALUE);
        if (maxCount <= 0) {
            return ToolResult.fail("Count must be positive");
        }
        Location dropLoc = target.getLocation();

        Material filter = null;
        if (matName != null && !matName.isEmpty()) {
            try {
                filter = Material.valueOf(matName.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                return ToolResult.fail("Unknown material: " + matName);
            }
        }

        // Iterate a snapshot copy: removeFromInventory structurally mutates the
        // live list backing getInventory(). Remove exactly what was dropped —
        // never wipe stacks that were not given away. Cloning the original stack
        // (not new ItemStack(...)) preserves enchantments/meta.
        int dropped = 0;
        List<ItemStack> snapshot = new ArrayList<>(agent.getBehaviorController().getInventory());
        for (ItemStack stack : snapshot) {
            if (filter != null && stack.getType() != filter) continue;
            int remaining = maxCount - dropped;
            if (remaining <= 0) break;
            int toDrop = Math.min(stack.getAmount(), remaining);

            ItemStack dropStack = stack.clone();
            dropStack.setAmount(toDrop);
            dropLoc.getWorld().dropItem(dropLoc, dropStack);
            agent.getBehaviorController().removeFromInventory(stack.getType(), toDrop);
            dropped += toDrop;
        }

        if (dropped == 0) return ToolResult.fail("No matching items in inventory");
        return ToolResult.ok("Dropped " + dropped + " items near " + playerName);
    }
}
