package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

public class GetPositionTool implements MinecraftTool {

    @Override public String getName() { return "get_position"; }

    @Override public String getDescription() {
        return "Get your current position, biome, time of day, and weather";
    }

    @Override public JsonObject getParameterSchema() {
        return new JsonObject(); // no params
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        Location loc = agent.getNpc().getLocation();
        World world = loc.getWorld();

        Biome biome = world.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String biomeName = biome.name().toLowerCase().replace('_', ' ');

        long time = world.getTime();
        String timeDesc;
        if (time < 1000) timeDesc = "dawn";
        else if (time < 6000) timeDesc = "morning";
        else if (time < 6500) timeDesc = "midday";
        else if (time < 12000) timeDesc = "afternoon";
        else if (time < 13000) timeDesc = "dusk";
        else timeDesc = "night";

        String weather;
        if (world.isThundering()) weather = "thunderstorm";
        else if (world.hasStorm()) weather = "rain";
        else weather = "clear";

        return ToolResult.ok("Position: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " | Biome: " + biomeName
                + " | Time: " + timeDesc
                + " | Weather: " + weather);
    }
}
