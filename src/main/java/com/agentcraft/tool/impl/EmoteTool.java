package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class EmoteTool implements MinecraftTool {

    @Override public String getName() { return "emote"; }

    @Override public String getDescription() {
        return "Perform a physical emote/gesture: wave (swing arm), nod (look down then up), or look at a player";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string: 'wave', 'nod', or 'look'");
        schema.addProperty("target", "string: player name (required for 'look')");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String type = params.has("type") ? params.get("type").getAsString() : "wave";
        FakePlayer npc = agent.getNpc();

        switch (type.toLowerCase()) {
            case "wave" -> {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    npc.swingArm(viewer);
                }
                return ToolResult.ok("Waved");
            }
            case "nod" -> {
                // Quick look down then back — simulates a nod
                org.bukkit.Location loc = npc.getLocation();
                org.bukkit.Location downTarget = loc.clone().add(
                        loc.getDirection().getX(), -2, loc.getDirection().getZ());
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    npc.lookAt(viewer, downTarget);
                }
                // Schedule look-back after 5 ticks
                Bukkit.getScheduler().runTaskLater(agent.getPlugin(), () -> {
                    org.bukkit.Location upTarget = loc.clone().add(
                            loc.getDirection().getX(), 1.6, loc.getDirection().getZ());
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        npc.lookAt(viewer, upTarget);
                    }
                }, 5L);
                return ToolResult.ok("Nodded");
            }
            case "look" -> {
                String target = params.has("target") ? params.get("target").getAsString() : null;
                if (target == null) return ToolResult.fail("No target specified for look");
                Player player = Bukkit.getPlayerExact(target);
                if (player == null) return ToolResult.fail("Player not found: " + target);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    npc.lookAt(viewer, player.getEyeLocation());
                }
                return ToolResult.ok("Looking at " + target);
            }
            default -> {
                return ToolResult.fail("Unknown emote: " + type + ". Use 'wave', 'nod', or 'look'.");
            }
        }
    }
}
