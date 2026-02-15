package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class BreedTool implements MinecraftTool {

    private static final double SEARCH_RADIUS = 8.0;

    private static final Map<String, Set<Material>> ANIMAL_FOOD = Map.of(
            "cow", Set.of(Material.WHEAT),
            "mooshroom", Set.of(Material.WHEAT),
            "sheep", Set.of(Material.WHEAT),
            "pig", Set.of(Material.CARROT, Material.POTATO, Material.BEETROOT),
            "chicken", Set.of(Material.WHEAT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS, Material.BEETROOT_SEEDS),
            "wolf", Set.of(Material.PORKCHOP, Material.COOKED_PORKCHOP, Material.BEEF, Material.COOKED_BEEF, Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON, Material.RABBIT, Material.COOKED_RABBIT),
            "cat", Set.of(Material.COD, Material.SALMON),
            "horse", Set.of(Material.GOLDEN_APPLE, Material.GOLDEN_CARROT),
            "rabbit", Set.of(Material.CARROT, Material.GOLDEN_CARROT, Material.DANDELION),
            "turtle", Set.of(Material.SEAGRASS)
    );

    @Override public String getName() { return "breed"; }

    @Override public String getDescription() {
        return "Feed and breed a nearby animal using appropriate food from inventory.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("animal", "string: animal type (cow, sheep, pig, chicken, etc.)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String animalName = params.has("animal") ? params.get("animal").getAsString().toLowerCase() : null;
        if (animalName == null || animalName.isEmpty()) return ToolResult.fail("No animal type specified");

        // Find food requirements
        Set<Material> foods = ANIMAL_FOOD.get(animalName);
        if (foods == null) {
            return ToolResult.fail("Unknown animal: " + animalName + ". Supported: cow, sheep, pig, chicken, wolf, cat, horse, rabbit, turtle");
        }

        // Check inventory for required food
        Material foodInInventory = null;
        for (Material food : foods) {
            if (agent.getBehaviorController().hasInInventory(food)) {
                foodInInventory = food;
                break;
            }
        }
        if (foodInInventory == null) {
            String foodNames = String.join(", ", foods.stream().map(m -> m.name().toLowerCase().replace('_', ' ')).toList());
            return ToolResult.fail("No food for " + animalName + " in inventory. Need: " + foodNames);
        }

        // Find nearest matching animal
        Location npcLoc = agent.getNpc().getLocation();
        Animals target = null;
        double nearest = Double.MAX_VALUE;

        for (Entity entity : npcLoc.getWorld().getNearbyEntities(npcLoc, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            if (!(entity instanceof Animals animal)) continue;
            String entityName = entity.getType().name().toLowerCase();
            if (!entityName.contains(animalName)) continue;
            if (animal.getLoveModeTicks() > 0) continue; // already in love mode

            double dist = entity.getLocation().distanceSquared(npcLoc);
            if (dist < nearest) {
                nearest = dist;
                target = animal;
            }
        }

        if (target == null) {
            return ToolResult.fail("No " + animalName + " found within " + (int) SEARCH_RADIUS + " blocks");
        }

        // Consume food and breed
        agent.getBehaviorController().removeFromInventory(foodInInventory);
        target.setLoveModeTicks(600); // 30 seconds

        // Navigate toward animal and swing arm
        agent.getBehaviorController().getNavigation().navigateTo(target.getLocation());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().swingArm(viewer);
        }

        return ToolResult.ok("Fed " + animalName + " with " + foodInInventory.name().toLowerCase().replace('_', ' ')
                + " — breeding mode activated");
    }
}
