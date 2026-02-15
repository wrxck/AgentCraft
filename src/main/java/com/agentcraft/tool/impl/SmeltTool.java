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
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class SmeltTool implements MinecraftTool {

    private static final Map<Material, Material> SMELT_RECIPES = new LinkedHashMap<>();
    private static final int FURNACE_SEARCH_RADIUS = 16;

    static {
        SMELT_RECIPES.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT_RECIPES.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_RECIPES.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT_RECIPES.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_RECIPES.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT_RECIPES.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT_RECIPES.put(Material.SAND, Material.GLASS);
        SMELT_RECIPES.put(Material.COBBLESTONE, Material.STONE);
        SMELT_RECIPES.put(Material.CLAY_BALL, Material.BRICK);
        SMELT_RECIPES.put(Material.NETHERRACK, Material.NETHER_BRICK);
        // Logs to charcoal
        SMELT_RECIPES.put(Material.OAK_LOG, Material.CHARCOAL);
        SMELT_RECIPES.put(Material.BIRCH_LOG, Material.CHARCOAL);
        SMELT_RECIPES.put(Material.SPRUCE_LOG, Material.CHARCOAL);
        SMELT_RECIPES.put(Material.DARK_OAK_LOG, Material.CHARCOAL);
        SMELT_RECIPES.put(Material.JUNGLE_LOG, Material.CHARCOAL);
        SMELT_RECIPES.put(Material.ACACIA_LOG, Material.CHARCOAL);
        // Deepslate ores
        SMELT_RECIPES.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_RECIPES.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_RECIPES.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        // Food
        SMELT_RECIPES.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        SMELT_RECIPES.put(Material.BEEF, Material.COOKED_BEEF);
        SMELT_RECIPES.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        SMELT_RECIPES.put(Material.COD, Material.COOKED_COD);
        SMELT_RECIPES.put(Material.SALMON, Material.COOKED_SALMON);
        SMELT_RECIPES.put(Material.MUTTON, Material.COOKED_MUTTON);
        SMELT_RECIPES.put(Material.RABBIT, Material.COOKED_RABBIT);
    }

    @Override public String getName() { return "smelt"; }

    @Override public String getDescription() {
        return "Smelt items in a nearby furnace. Consumes input + fuel from inventory.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("input", "string: material to smelt (e.g. iron_ore, sand, raw_iron)");
        schema.addProperty("fuel", "string: fuel material (default: coal or charcoal)");
        schema.addProperty("count", "integer: how many to smelt (default 1)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String inputName = params.has("input") ? params.get("input").getAsString().toLowerCase().replace(' ', '_') : null;
        if (inputName == null || inputName.isEmpty()) return ToolResult.fail("No input material specified");

        int count = params.has("count") ? params.get("count").getAsInt() : 1;
        count = Math.max(1, Math.min(count, 64));

        // Find the input material in recipes
        Material inputMat = findSmeltInput(inputName);
        if (inputMat == null) {
            return ToolResult.fail("Can't smelt " + inputName + ". Smeltable: iron_ore, raw_iron, gold_ore, sand, cobblestone, log, raw foods");
        }

        Material outputMat = SMELT_RECIPES.get(inputMat);

        // Check for nearby furnace
        Location furnaceLoc = findNearestFurnace(agent);
        if (furnaceLoc == null) {
            return ToolResult.fail("No furnace within " + FURNACE_SEARCH_RADIUS + " blocks");
        }

        // Check input availability
        int available = agent.getBehaviorController().countInInventory(inputMat);
        int toSmelt = Math.min(count, available);
        if (toSmelt == 0) {
            return ToolResult.fail("No " + inputName + " in inventory");
        }

        // Check fuel
        Material fuelMat = findFuel(agent, params);
        if (fuelMat == null) {
            return ToolResult.fail("No fuel in inventory (need coal, charcoal, or other fuel)");
        }

        // Each fuel smelts 8 items; calculate fuel needed
        int fuelNeeded = (int) Math.ceil(toSmelt / 8.0);
        int fuelAvailable = agent.getBehaviorController().countInInventory(fuelMat);
        if (fuelAvailable < fuelNeeded) {
            toSmelt = fuelAvailable * 8;
            fuelNeeded = fuelAvailable;
        }
        if (toSmelt == 0) {
            return ToolResult.fail("Not enough fuel");
        }

        // Walk to furnace, consume materials, produce output
        agent.getBehaviorController().getNavigation().navigateTo(furnaceLoc);

        // Consume input and fuel
        agent.getBehaviorController().removeFromInventory(inputMat, toSmelt);
        agent.getBehaviorController().removeFromInventory(fuelMat, fuelNeeded);

        // Add output
        agent.getBehaviorController().addToInventory(new ItemStack(outputMat, toSmelt));

        // Visual: arm swing at furnace
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().swingArm(viewer);
        }

        return ToolResult.ok("Smelted " + toSmelt + "x " + inputMat.name().toLowerCase().replace('_', ' ')
                + " → " + toSmelt + "x " + outputMat.name().toLowerCase().replace('_', ' '));
    }

    private Material findSmeltInput(String name) {
        String upper = name.toUpperCase().replace(' ', '_');
        for (Material mat : SMELT_RECIPES.keySet()) {
            if (mat.name().equalsIgnoreCase(upper) || mat.name().contains(upper)) {
                return mat;
            }
        }
        return null;
    }

    private Location findNearestFurnace(AIAgent agent) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX(), cy = npcLoc.getBlockY(), cz = npcLoc.getBlockZ();

        Block nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int dx = -FURNACE_SEARCH_RADIUS; dx <= FURNACE_SEARCH_RADIUS; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -FURNACE_SEARCH_RADIUS; dz <= FURNACE_SEARCH_RADIUS; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material m = b.getType();
                    if (m == Material.FURNACE || m == Material.BLAST_FURNACE || m == Material.SMOKER) {
                        double dist = b.getLocation().distanceSquared(npcLoc);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = b;
                        }
                    }
                }
            }
        }

        return nearest != null ? nearest.getLocation().add(0.5, 0, 0.5) : null;
    }

    private Material findFuel(AIAgent agent, JsonObject params) {
        // Check explicit fuel parameter
        if (params.has("fuel") && !params.get("fuel").isJsonNull()) {
            String fuelName = params.get("fuel").getAsString().toUpperCase().replace(' ', '_');
            try {
                Material m = Material.valueOf(fuelName);
                if (agent.getBehaviorController().hasInInventory(m)) return m;
            } catch (IllegalArgumentException ignored) {}
        }

        // Auto-detect: prefer coal, then charcoal, then any log
        if (agent.getBehaviorController().hasInInventory(Material.COAL)) return Material.COAL;
        if (agent.getBehaviorController().hasInInventory(Material.CHARCOAL)) return Material.CHARCOAL;

        // Any log as fuel
        for (ItemStack stack : agent.getBehaviorController().getInventory()) {
            if (stack.getType().name().endsWith("_LOG")) return stack.getType();
        }
        for (ItemStack stack : agent.getBehaviorController().getInventory()) {
            if (stack.getType().name().endsWith("_PLANKS")) return stack.getType();
        }

        return null;
    }
}
