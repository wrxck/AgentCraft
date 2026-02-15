package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SleepTool implements MinecraftTool {

    private static final int SEARCH_RADIUS = 8;
    private static final int SLEEP_TICKS = 100; // 5 seconds

    @Override public String getName() { return "sleep"; }

    @Override public String getDescription() {
        return "Sleep in a nearby bed (cosmetic). Wakes after a few seconds or when next tool is called.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("x", "integer: bed X coordinate (optional, auto-finds nearby bed)");
        schema.addProperty("y", "integer: bed Y coordinate (optional)");
        schema.addProperty("z", "integer: bed Z coordinate (optional)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        Location bedLoc;

        if (params.has("x") && params.has("y") && params.has("z")) {
            try {
                int x = params.get("x").getAsInt();
                int y = params.get("y").getAsInt();
                int z = params.get("z").getAsInt();
                bedLoc = new Location(agent.getNpc().getLocation().getWorld(), x, y, z);
                if (!isBed(bedLoc.getBlock())) {
                    return ToolResult.fail("No bed at " + x + " " + y + " " + z);
                }
            } catch (Exception e) {
                return ToolResult.fail("Invalid coordinates");
            }
        } else {
            bedLoc = findNearestBed(agent);
            if (bedLoc == null) {
                return ToolResult.fail("No bed found within " + SEARCH_RADIUS + " blocks");
            }
        }

        // Navigate to bed
        agent.getBehaviorController().getNavigation().navigateTo(bedLoc);

        // Set sleeping pose
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().setPose(viewer, true);
        }

        // Wake up after SLEEP_TICKS
        Bukkit.getScheduler().runTaskLater(agent.getPlugin(), () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                agent.getNpc().setPose(viewer, false);
            }
        }, SLEEP_TICKS);

        return ToolResult.ok("Sleeping in bed at " + bedLoc.getBlockX() + " " + bedLoc.getBlockY() + " " + bedLoc.getBlockZ());
    }

    private Location findNearestBed(AIAgent agent) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX(), cy = npcLoc.getBlockY(), cz = npcLoc.getBlockZ();

        double nearest = Double.MAX_VALUE;
        Block best = null;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (isBed(b)) {
                        double dist = b.getLocation().distanceSquared(npcLoc);
                        if (dist < nearest) {
                            nearest = dist;
                            best = b;
                        }
                    }
                }
            }
        }

        return best != null ? best.getLocation().add(0.5, 0, 0.5) : null;
    }

    private boolean isBed(Block block) {
        return block.getType().name().endsWith("_BED");
    }
}
