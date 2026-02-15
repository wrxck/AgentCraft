package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.agentcraft.util.LocationUtil;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class GotoTool implements MinecraftTool {

    @Override public String getName() { return "goto"; }

    @Override public String getDescription() {
        return "Walk to coordinates or a player/NPC by name";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("target", "string: player name, NPC name, or 'x y z' coordinates");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String target = params.has("target") ? params.get("target").getAsString() : null;
        if (target == null || target.isEmpty()) return ToolResult.fail("No target specified");

        Location loc = resolveTarget(agent, target);
        if (loc == null) return ToolResult.fail("Could not find target: " + target);

        agent.getBehaviorController().getNavigation().navigateTo(loc);
        return ToolResult.ok("Walking to " + target);
    }

    private Location resolveTarget(AIAgent agent, String target) {
        // Try coordinates
        String[] parts = target.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                int x = Integer.parseInt(parts[0]);
                int z;
                int y;
                if (parts.length >= 3) {
                    y = Integer.parseInt(parts[1]);
                    z = Integer.parseInt(parts[2]);
                } else {
                    z = Integer.parseInt(parts[1]);
                    y = agent.getNpc().getLocation().getBlockY();
                }
                Location loc = new Location(agent.getNpc().getLocation().getWorld(), x + 0.5, y, z + 0.5);
                return LocationUtil.findSafeGround(loc);
            } catch (NumberFormatException ignored) {}
        }

        // Try player name
        Player player = Bukkit.getPlayerExact(target.trim());
        if (player != null && player.getWorld().equals(agent.getNpc().getLocation().getWorld())) {
            return player.getLocation();
        }

        // Try NPC name
        for (AIAgent other : AgentManager.getInstance().getAllAgents()) {
            if (other != agent && other.getNpc().getName().equalsIgnoreCase(target.trim())) {
                return other.getNpc().getLocation();
            }
        }

        return null;
    }
}
