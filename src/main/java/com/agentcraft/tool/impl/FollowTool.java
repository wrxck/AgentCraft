package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FollowTool implements MinecraftTool {

    @Override public String getName() { return "follow"; }

    @Override public String getDescription() {
        return "Follow a player around";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("player", "string: player name to follow");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String name = params.has("player") ? params.get("player").getAsString() : null;
        if (name == null || name.isEmpty()) return ToolResult.fail("No player specified");

        Player target = Bukkit.getPlayerExact(name);
        if (target == null) return ToolResult.fail("Player not found: " + name);

        agent.getBehaviorController().startFollowing(target);
        return ToolResult.ok("Following " + name);
    }
}
