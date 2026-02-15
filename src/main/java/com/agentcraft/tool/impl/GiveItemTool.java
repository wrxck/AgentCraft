package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
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
        String playerName = params.has("player") ? params.get("player").getAsString() : null;
        if (playerName == null || playerName.isEmpty()) return ToolResult.fail("No player specified");

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) return ToolResult.fail("Player not found: " + playerName);

        if (!target.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
            return ToolResult.fail("Player is in a different world");
        }

        String matName = params.has("material") ? params.get("material").getAsString() : null;
        int maxCount = params.has("count") ? params.get("count").getAsInt() : Integer.MAX_VALUE;
        Location dropLoc = target.getLocation();

        int dropped = 0;

        if (matName == null || matName.isEmpty()) {
            // Give everything
            for (ItemStack stack : agent.getBehaviorController().getInventory()) {
                if (dropped >= maxCount) break;
                dropLoc.getWorld().dropItem(dropLoc, stack.clone());
                dropped += stack.getAmount();
            }
            // Clear inventory
            while (!agent.getBehaviorController().getInventory().isEmpty()) {
                ItemStack first = agent.getBehaviorController().getInventory().get(0);
                agent.getBehaviorController().removeFromInventory(first.getType());
            }
        } else {
            // Give specific material
            String upperMat = matName.toUpperCase().replace(' ', '_');
            Material material;
            try {
                material = Material.valueOf(upperMat);
            } catch (IllegalArgumentException e) {
                return ToolResult.fail("Unknown material: " + matName);
            }

            while (dropped < maxCount && agent.getBehaviorController().hasInInventory(material)) {
                agent.getBehaviorController().removeFromInventory(material);
                dropLoc.getWorld().dropItem(dropLoc, new ItemStack(material));
                dropped++;
            }
        }

        if (dropped == 0) return ToolResult.fail("No matching items in inventory");
        return ToolResult.ok("Dropped " + dropped + " items near " + playerName);
    }
}
