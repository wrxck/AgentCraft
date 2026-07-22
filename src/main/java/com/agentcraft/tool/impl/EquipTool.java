package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.AgentEquipment;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
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
        try {
            return run(agent, params);
        } catch (ToolArgs.BadArgument e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private ToolResult run(AIAgent agent, JsonObject params) {
        String rawSlot = ToolArgs.optString(params, "slot");
        String slotName = rawSlot != null ? rawSlot.toLowerCase() : null;
        String rawMat = ToolArgs.optString(params, "material");
        String matName = rawMat != null ? rawMat.toLowerCase().replace(' ', '_') : null;

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

        // Clone BEFORE removal so enchantments/meta survive; remove one item.
        ItemStack equipItem = found.clone();
        equipItem.setAmount(1);
        agent.getBehaviorController().removeFromInventory(foundMat);

        // Update the persistent equipment model so respawns/late joiners see it
        // too, and recover any item previously equipped in this slot.
        FakePlayer npc = agent.getNpc();
        AgentEquipment current = npc.getEquipment();
        EquipmentOverlay overlay = current instanceof EquipmentOverlay existing
                ? existing
                : new EquipmentOverlay(current);
        ItemStack previous = overlay.put(slot, equipItem);
        npc.setEquipment(overlay);

        if (previous != null) {
            // Return the replaced item to inventory instead of voiding it.
            agent.getBehaviorController().addToInventory(previous);
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.sendEquipmentSlot(viewer, slot, equipItem);
        }

        return ToolResult.ok("Equipped " + foundMat.name().toLowerCase().replace('_', ' ') + " to " + slotName);
    }
}
