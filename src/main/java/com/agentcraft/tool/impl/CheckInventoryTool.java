package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckInventoryTool implements MinecraftTool {

    @Override public String getName() { return "check_inventory"; }

    @Override public String getDescription() {
        return "Check what items you are carrying in your inventory";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject(); // no params
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        List<ItemStack> inventory = agent.getBehaviorController().getInventory();

        if (inventory.isEmpty()) {
            return ToolResult.ok("Inventory is empty");
        }

        Map<String, Integer> items = new LinkedHashMap<>();
        for (ItemStack stack : inventory) {
            String name = stack.getType().name().toLowerCase().replace('_', ' ');
            items.merge(name, stack.getAmount(), Integer::sum);
        }

        StringBuilder sb = new StringBuilder("Inventory: ");
        items.forEach((name, count) -> sb.append(count).append("x ").append(name).append(", "));
        sb.setLength(sb.length() - 2);
        return ToolResult.ok(sb.toString());
    }
}
