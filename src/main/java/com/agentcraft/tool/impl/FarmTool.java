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
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class FarmTool implements MinecraftTool {

    private static final int SEARCH_RADIUS = 8;

    private static final Map<Material, Material> SEED_TO_CROP = Map.of(
            Material.WHEAT_SEEDS, Material.WHEAT,
            Material.CARROT, Material.CARROTS,
            Material.POTATO, Material.POTATOES,
            Material.BEETROOT_SEEDS, Material.BEETROOTS,
            Material.MELON_SEEDS, Material.MELON_STEM,
            Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM
    );

    @Override public String getName() { return "farm"; }

    @Override public String getDescription() {
        return "Farm actions: hoe dirt to farmland, plant seeds, or harvest mature crops.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("action", "string: plant, harvest, or hoe");
        schema.addProperty("x", "integer: X coordinate (optional, auto-finds nearby)");
        schema.addProperty("y", "integer: Y coordinate (optional)");
        schema.addProperty("z", "integer: Z coordinate (optional)");
        schema.addProperty("crop", "string: crop type for planting (optional, uses first seeds in inventory)");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String action = params.has("action") ? params.get("action").getAsString().toLowerCase() : null;
        if (action == null || action.isEmpty()) return ToolResult.fail("No action specified. Use: plant, harvest, or hoe");

        Location target = getTargetLocation(agent, params);

        return switch (action) {
            case "hoe" -> doHoe(agent, target);
            case "plant" -> doPlant(agent, params, target);
            case "harvest" -> doHarvest(agent, target);
            default -> ToolResult.fail("Unknown farm action: " + action + ". Use: plant, harvest, or hoe");
        };
    }

    private ToolResult doHoe(AIAgent agent, Location target) {
        if (target == null) {
            target = findNearbyBlock(agent, Material.GRASS_BLOCK, Material.DIRT);
        }
        if (target == null) return ToolResult.fail("No dirt or grass block nearby to hoe");

        Block block = target.getBlock();
        Material mat = block.getType();
        if (mat != Material.GRASS_BLOCK && mat != Material.DIRT) {
            return ToolResult.fail("Block at " + formatCoords(target) + " is " + mat.name().toLowerCase() + ", not dirt/grass");
        }

        block.setType(Material.FARMLAND);
        swingArm(agent);

        return ToolResult.ok("Hoed dirt at " + formatCoords(target) + " into farmland");
    }

    private ToolResult doPlant(AIAgent agent, JsonObject params, Location target) {
        // Find seeds in inventory
        Material seedMat = null;
        if (params.has("crop") && !params.get("crop").isJsonNull()) {
            String cropName = params.get("crop").getAsString().toUpperCase().replace(' ', '_');
            for (Material m : SEED_TO_CROP.keySet()) {
                if (m.name().contains(cropName) || cropName.contains(m.name().replace("_SEEDS", ""))) {
                    if (agent.getBehaviorController().hasInInventory(m)) {
                        seedMat = m;
                        break;
                    }
                }
            }
        }
        if (seedMat == null) {
            // Find any seeds in inventory
            for (Material m : SEED_TO_CROP.keySet()) {
                if (agent.getBehaviorController().hasInInventory(m)) {
                    seedMat = m;
                    break;
                }
            }
        }
        if (seedMat == null) return ToolResult.fail("No seeds in inventory");

        if (target == null) {
            target = findNearbyFarmland(agent);
        }
        if (target == null) return ToolResult.fail("No farmland nearby to plant on");

        // Check that target has farmland below and air at target
        Block block = target.getBlock();
        Block below = target.clone().add(0, -1, 0).getBlock();

        // If target IS farmland, plant one block above
        if (block.getType() == Material.FARMLAND) {
            target = target.clone().add(0, 1, 0);
            block = target.getBlock();
        }

        if (block.getType() != Material.AIR) {
            return ToolResult.fail("Block at " + formatCoords(target) + " is not empty");
        }

        below = target.clone().add(0, -1, 0).getBlock();
        if (below.getType() != Material.FARMLAND) {
            return ToolResult.fail("No farmland below " + formatCoords(target));
        }

        Material cropMat = SEED_TO_CROP.get(seedMat);
        block.setType(cropMat);
        agent.getBehaviorController().removeFromInventory(seedMat);
        swingArm(agent);

        return ToolResult.ok("Planted " + seedMat.name().toLowerCase().replace('_', ' ') + " at " + formatCoords(target));
    }

    private ToolResult doHarvest(AIAgent agent, Location target) {
        if (target == null) {
            target = findMatureCrop(agent);
        }
        if (target == null) return ToolResult.fail("No mature crops nearby to harvest");

        Block block = target.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return ToolResult.fail("Block at " + formatCoords(target) + " is not a crop");
        }

        if (ageable.getAge() < ageable.getMaximumAge()) {
            return ToolResult.fail("Crop at " + formatCoords(target) + " is not yet mature");
        }

        // Break and collect drops
        Location dropLoc = target.clone();
        block.breakNaturally();
        swingArm(agent);

        // Collect drops after a slight delay
        Bukkit.getScheduler().runTaskLater(agent.getPlugin(), () -> {
            for (Entity entity : dropLoc.getWorld().getNearbyEntities(dropLoc, 2, 2, 2)) {
                if (entity instanceof Item item) {
                    agent.getBehaviorController().addToInventory(item.getItemStack().clone());
                    item.remove();
                }
            }
        }, 5L);

        return ToolResult.ok("Harvested crop at " + formatCoords(target));
    }

    private Location getTargetLocation(AIAgent agent, JsonObject params) {
        if (params.has("x") && params.has("y") && params.has("z")) {
            try {
                int x = params.get("x").getAsInt();
                int y = params.get("y").getAsInt();
                int z = params.get("z").getAsInt();
                return new Location(agent.getNpc().getLocation().getWorld(), x, y, z);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Location findNearbyBlock(AIAgent agent, Material... types) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX(), cy = npcLoc.getBlockY(), cz = npcLoc.getBlockZ();

        double nearest = Double.MAX_VALUE;
        Block best = null;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    for (Material t : types) {
                        if (b.getType() == t) {
                            double dist = b.getLocation().distanceSquared(npcLoc);
                            if (dist < nearest) {
                                nearest = dist;
                                best = b;
                            }
                        }
                    }
                }
            }
        }

        return best != null ? best.getLocation() : null;
    }

    private Location findNearbyFarmland(AIAgent agent) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX(), cy = npcLoc.getBlockY(), cz = npcLoc.getBlockZ();

        double nearest = Double.MAX_VALUE;
        Block best = null;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() == Material.FARMLAND) {
                        Block above = world.getBlockAt(cx + dx, cy + dy + 1, cz + dz);
                        if (above.getType() == Material.AIR) {
                            double dist = b.getLocation().distanceSquared(npcLoc);
                            if (dist < nearest) {
                                nearest = dist;
                                best = b;
                            }
                        }
                    }
                }
            }
        }

        return best != null ? best.getLocation() : null;
    }

    private Location findMatureCrop(AIAgent agent) {
        Location npcLoc = agent.getNpc().getLocation();
        World world = npcLoc.getWorld();
        int cx = npcLoc.getBlockX(), cy = npcLoc.getBlockY(), cz = npcLoc.getBlockZ();

        double nearest = Double.MAX_VALUE;
        Block best = null;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() >= ageable.getMaximumAge()) {
                            double dist = b.getLocation().distanceSquared(npcLoc);
                            if (dist < nearest) {
                                nearest = dist;
                                best = b;
                            }
                        }
                    }
                }
            }
        }

        return best != null ? best.getLocation() : null;
    }

    private void swingArm(AIAgent agent) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            agent.getNpc().swingArm(viewer);
        }
    }

    private String formatCoords(Location loc) {
        return loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}
