package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class ScanAreaTool implements MinecraftTool {

    @Override public String getName() { return "scan_area"; }

    @Override public String getDescription() {
        return "Scan the nearby area for specific blocks or entities and report what's found";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("target", "string: what to scan for — a block material name (e.g. diamond_ore) or 'mobs' or 'players'");
        schema.addProperty("radius", "integer: scan radius in blocks (default 16, max 32)");
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
        String target = ToolArgs.optString(params, "target");
        if (target == null || target.isEmpty()) return ToolResult.fail("No scan target specified");

        int radius = ToolArgs.optInt(params, "radius", 16);
        radius = Math.max(1, Math.min(radius, 32));

        Location npcLoc = agent.getNpc().getLocation();

        if (target.equalsIgnoreCase("mobs") || target.equalsIgnoreCase("entities")) {
            return scanEntities(npcLoc, radius);
        }
        if (target.equalsIgnoreCase("players")) {
            return scanPlayers(npcLoc, radius);
        }

        return scanBlocks(npcLoc, radius, target.toUpperCase().replace(' ', '_'));
    }

    private ToolResult scanBlocks(Location center, int radius, String materialName) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Map<String, Integer> found = new LinkedHashMap<>();
        Location nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    String name = b.getType().name();
                    if (name.contains(materialName) || name.equalsIgnoreCase(materialName)) {
                        found.merge(name, 1, Integer::sum);
                        double distSq = b.getLocation().distanceSquared(center);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = b.getLocation();
                        }
                    }
                }
            }
        }

        if (found.isEmpty()) {
            return ToolResult.ok("No " + materialName.toLowerCase().replace('_', ' ')
                    + " found within " + radius + " blocks");
        }

        StringBuilder sb = new StringBuilder("Found: ");
        found.forEach((name, count) ->
                sb.append(count).append("x ").append(name.toLowerCase().replace('_', ' ')).append(", "));
        sb.setLength(sb.length() - 2);
        if (nearest != null) {
            sb.append(". Nearest at ").append(nearest.getBlockX())
                    .append(" ").append(nearest.getBlockY())
                    .append(" ").append(nearest.getBlockZ());
        }
        return ToolResult.ok(sb.toString());
    }

    private ToolResult scanEntities(Location center, int radius) {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player) continue;
            if (entity instanceof LivingEntity) {
                found.merge(entity.getType().name().toLowerCase().replace('_', ' '), 1, Integer::sum);
            }
        }

        if (found.isEmpty()) return ToolResult.ok("No mobs found within " + radius + " blocks");

        StringBuilder sb = new StringBuilder("Mobs nearby: ");
        found.forEach((name, count) -> sb.append(count).append("x ").append(name).append(", "));
        sb.setLength(sb.length() - 2);
        return ToolResult.ok(sb.toString());
    }

    private ToolResult scanPlayers(Location center, int radius) {
        StringBuilder sb = new StringBuilder();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player) {
                int dist = (int) Math.round(player.getLocation().distance(center));
                if (sb.length() > 0) sb.append(", ");
                sb.append(player.getName()).append(" (").append(dist).append(" blocks)");
            }
        }

        if (sb.length() == 0) return ToolResult.ok("No players found within " + radius + " blocks");
        return ToolResult.ok("Players: " + sb);
    }
}
