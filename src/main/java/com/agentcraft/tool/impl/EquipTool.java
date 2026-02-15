package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class EquipTool implements MinecraftTool {

    private static final Map<String, EnumWrappers.ItemSlot> SLOT_MAP = Map.of(
            "hand", EnumWrappers.ItemSlot.MAINHAND,
            "mainhand", EnumWrappers.ItemSlot.MAINHAND,
            "head", EnumWrappers.ItemSlot.HEAD,
            "helmet", EnumWrappers.ItemSlot.HEAD,
            "chest", EnumWrappers.ItemSlot.CHEST,
            "chestplate", EnumWrappers.ItemSlot.CHEST,
            "legs", EnumWrappers.ItemSlot.LEGS,
            "leggings", EnumWrappers.ItemSlot.LEGS,
            "feet", EnumWrappers.ItemSlot.FEET,
            "boots", EnumWrappers.ItemSlot.FEET
    );

    @Override public String getName() { return "equip"; }

    @Override public String getDescription() {
        return "Equip an item from inventory to a slot (hand, head, chest, legs, feet).";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("slot", "string: hand, head, chest, legs, or feet");
        schema.addProperty("material", "string: material name to equip");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String slotName = params.has("slot") ? params.get("slot").getAsString().toLowerCase() : null;
        String matName = params.has("material") ? params.get("material").getAsString().toLowerCase().replace(' ', '_') : null;

        if (slotName == null || slotName.isEmpty()) return ToolResult.fail("No slot specified");
        if (matName == null || matName.isEmpty()) return ToolResult.fail("No material specified");

        EnumWrappers.ItemSlot slot = SLOT_MAP.get(slotName);
        if (slot == null) return ToolResult.fail("Invalid slot. Use: hand, head, chest, legs, or feet");

        // Find matching material in inventory
        String upperMat = matName.toUpperCase();
        ItemStack found = null;
        Material foundMat = null;

        for (ItemStack stack : agent.getBehaviorController().getInventory()) {
            String name = stack.getType().name();
            if (name.equalsIgnoreCase(upperMat) || name.contains(upperMat)) {
                found = stack;
                foundMat = stack.getType();
                break;
            }
        }

        if (found == null || foundMat == null) {
            return ToolResult.fail("No " + matName + " in inventory");
        }

        // Remove from inventory and send equipment packet
        agent.getBehaviorController().removeFromInventory(foundMat);
        ItemStack equipItem = new ItemStack(foundMat);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().sendEquipmentSlot(viewer, slot, equipItem);
        }

        return ToolResult.ok("Equipped " + foundMat.name().toLowerCase().replace('_', ' ') + " to " + slotName);
    }
}
