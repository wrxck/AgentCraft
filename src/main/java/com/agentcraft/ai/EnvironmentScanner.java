package com.agentcraft.ai;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class EnvironmentScanner {

    private static final int BLOCK_SCAN_RADIUS = 8;
    private static final double ENTITY_SCAN_RADIUS = 12.0;

    private static final Set<EntityType> HOSTILE_MOBS = EnumSet.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
            EntityType.SPIDER, EntityType.WITCH, EntityType.ENDERMAN,
            EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.HUSK,
            EntityType.STRAY, EntityType.PHANTOM, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.VEX,
            EntityType.EVOKER, EntityType.BLAZE, EntityType.GHAST,
            EntityType.WITHER_SKELETON, EntityType.SLIME, EntityType.MAGMA_CUBE
    );

    private static final Set<EntityType> PASSIVE_MOBS = EnumSet.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.HORSE, EntityType.DONKEY, EntityType.CAT, EntityType.WOLF,
            EntityType.RABBIT, EntityType.VILLAGER, EntityType.IRON_GOLEM,
            EntityType.BEE, EntityType.FOX, EntityType.PARROT, EntityType.TURTLE,
            EntityType.FROG, EntityType.ALLAY, EntityType.ARMADILLO
    );

    public static String scan(AIAgent agent) {
        Location loc = agent.getNpc().getLocation();
        World world = loc.getWorld();
        StringBuilder sb = new StringBuilder();

        sb.append("[SURROUNDINGS]\n");

        // Location and biome
        Biome biome = world.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String biomeName = formatBiomeName(biome.name());
        sb.append("You are in a ").append(biomeName).append(" biome at roughly X=")
                .append(loc.getBlockX()).append(", Z=").append(loc.getBlockZ()).append(".\n");

        // Time and weather
        sb.append(describeTimeAndWeather(world)).append("\n");

        // Nearby blocks summary
        String blockSummary = scanNearbyBlocks(loc);
        if (!blockSummary.isEmpty()) {
            sb.append("Nearby: ").append(blockSummary).append("\n");
        }

        // Interactable blocks with exact coordinates
        String interactables = scanInteractableBlocks(loc);
        if (!interactables.isEmpty()) {
            sb.append("Interactable blocks:\n").append(interactables);
        }

        // Nearby entities
        List<Entity> nearby = new ArrayList<>(world.getNearbyEntities(loc, ENTITY_SCAN_RADIUS, ENTITY_SCAN_RADIUS, ENTITY_SCAN_RADIUS));

        // Hostile mobs
        List<String> hostiles = new ArrayList<>();
        List<String> passives = new ArrayList<>();
        for (Entity entity : nearby) {
            if (HOSTILE_MOBS.contains(entity.getType())) {
                int dist = (int) Math.round(entity.getLocation().distance(loc));
                String dir = describeDirection(loc, entity.getLocation());
                hostiles.add(formatEntityName(entity.getType().name()) + " (" + dist + " blocks " + dir + ")");
            } else if (PASSIVE_MOBS.contains(entity.getType())) {
                int dist = (int) Math.round(entity.getLocation().distance(loc));
                passives.add(formatEntityName(entity.getType().name()) + " (" + dist + " blocks away)");
            }
        }

        if (!hostiles.isEmpty()) {
            sb.append("Hostile mobs: ").append(String.join(", ", hostiles)).append("\n");
        }
        if (!passives.isEmpty()) {
            // Group passives by type for brevity
            sb.append("Passive mobs: ").append(summarizeEntities(passives)).append("\n");
        }

        // Nearby players
        List<String> playerDescs = new ArrayList<>();
        for (Entity entity : nearby) {
            if (entity instanceof Player player) {
                int dist = (int) Math.round(player.getLocation().distance(loc));
                playerDescs.add(player.getName() + " (" + dist + " blocks away)");
            }
        }
        // Also check for other NPCs
        for (AIAgent other : AgentManager.getInstance().getAllAgents()) {
            if (other == agent) continue;
            Location otherLoc = other.getNpc().getLocation();
            if (!otherLoc.getWorld().equals(world)) continue;
            double dist = otherLoc.distance(loc);
            if (dist <= ENTITY_SCAN_RADIUS) {
                playerDescs.add(other.getNpc().getName() + " (NPC, " + (int) Math.round(dist) + " blocks away)");
            }
        }
        if (!playerDescs.isEmpty()) {
            sb.append("Players: ").append(String.join(", ", playerDescs)).append("\n");
        }

        // NPC inventory
        List<ItemStack> inventory = agent.getBehaviorController().getInventory();
        if (!inventory.isEmpty()) {
            Map<String, Integer> items = new LinkedHashMap<>();
            for (ItemStack stack : inventory) {
                String name = formatMaterialName(stack.getType().name());
                items.merge(name, stack.getAmount(), Integer::sum);
            }
            List<String> itemDescs = new ArrayList<>();
            items.forEach((name, count) -> itemDescs.add(count + " " + name));
            sb.append("Inventory: ").append(String.join(", ", itemDescs)).append("\n");
        }

        // Home location
        Location homeLoc = agent.getBehaviorController().getHomeLocation();
        sb.append("Your home is at X=").append(homeLoc.getBlockX())
                .append(" Y=").append(homeLoc.getBlockY())
                .append(" Z=").append(homeLoc.getBlockZ()).append(".\n");

        // NPC state
        String stateDesc = switch (agent.getState()) {
            case IDLE -> "standing idle near your home location";
            case THINKING -> "thinking about a task";
            case WORKING -> "working on a task";
            case PAUSED -> "paused";
            case ON_EXPEDITION -> "on an expedition far from home";
        };
        sb.append("You are ").append(stateDesc).append(".");

        return sb.toString();
    }

    private static String describeTimeAndWeather(World world) {
        long time = world.getTime();
        String timeDesc;
        if (time < 1000) timeDesc = "dawn";
        else if (time < 6000) timeDesc = "morning";
        else if (time < 6500) timeDesc = "midday";
        else if (time < 12000) timeDesc = "afternoon";
        else if (time < 13000) timeDesc = "dusk";
        else timeDesc = "night";

        String weather;
        if (world.isThundering()) weather = "a thunderstorm is raging";
        else if (world.hasStorm()) weather = "it is raining";
        else weather = "the sky is clear";

        return "It is " + timeDesc + " and " + weather + ".";
    }

    private static String scanNearbyBlocks(Location center) {
        Map<String, Integer> features = new LinkedHashMap<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = -BLOCK_SCAN_RADIUS; x <= BLOCK_SCAN_RADIUS; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -BLOCK_SCAN_RADIUS; z <= BLOCK_SCAN_RADIUS; z++) {
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    String feature = categorizeBlock(block.getType());
                    if (feature != null) {
                        features.merge(feature, 1, Integer::sum);
                    }
                }
            }
        }

        List<String> descriptions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : features.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue();
            if (count > 5) {
                descriptions.add("lots of " + name);
            } else if (count > 1) {
                descriptions.add("some " + name);
            } else {
                descriptions.add(name);
            }
        }

        return String.join(", ", descriptions);
    }

    private static String scanInteractableBlocks(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Collect interactable blocks with coordinates, sorted by distance
        List<String> entries = new ArrayList<>();

        for (int x = -BLOCK_SCAN_RADIUS; x <= BLOCK_SCAN_RADIUS; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -BLOCK_SCAN_RADIUS; z <= BLOCK_SCAN_RADIUS; z++) {
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    String desc = categorizeInteractable(block.getType());
                    if (desc != null) {
                        int bx = cx + x, by = cy + y, bz = cz + z;
                        int dist = Math.abs(x) + Math.abs(y) + Math.abs(z);
                        entries.add(dist + "|  " + desc + " at " + bx + " " + by + " " + bz + "\n");
                    }
                }
            }
        }

        // Sort by distance and cap at 8 entries
        entries.sort(Comparator.comparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf('|')))));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String entry : entries) {
            if (count >= 8) break;
            sb.append(entry.substring(entry.indexOf('|') + 1));
            count++;
        }
        return sb.toString();
    }

    private static String categorizeInteractable(Material mat) {
        String name = mat.name();
        if (name.contains("ORE")) return formatMaterialName(name);
        if (name.endsWith("_LOG")) return formatMaterialName(name);
        if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL) return formatMaterialName(name);
        if (mat == Material.CRAFTING_TABLE) return "crafting table";
        if (mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER) return formatMaterialName(name);
        if (mat == Material.ANVIL) return "anvil";
        if (mat == Material.ENCHANTING_TABLE) return "enchanting table";
        if (mat == Material.BREWING_STAND) return "brewing stand";
        return null;
    }

    private static String categorizeBlock(Material mat) {
        String name = mat.name();
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")) return "trees";
        if (name.endsWith("_LEAVES")) return null; // covered by "trees"
        if (mat == Material.WATER) return "water";
        if (mat == Material.LAVA) return "lava";
        if (name.contains("FLOWER") || mat == Material.DANDELION || mat == Material.POPPY
                || mat == Material.BLUE_ORCHID || mat == Material.ALLIUM
                || mat == Material.AZURE_BLUET || name.contains("TULIP")
                || mat == Material.OXEYE_DAISY || mat == Material.CORNFLOWER
                || mat == Material.LILY_OF_THE_VALLEY || mat == Material.SUNFLOWER
                || mat == Material.LILAC || mat == Material.ROSE_BUSH || mat == Material.PEONY) {
            return "flowers";
        }
        if (mat == Material.SHORT_GRASS || mat == Material.TALL_GRASS || mat == Material.FERN) return "tall grass";
        if (name.contains("ORE")) return "exposed ore";
        if (mat == Material.CHEST) return "a chest";
        if (mat == Material.CRAFTING_TABLE) return "a crafting table";
        if (mat == Material.FURNACE) return "a furnace";
        if (mat == Material.TORCH || mat == Material.WALL_TORCH) return "torches";
        if (name.contains("FENCE")) return "fences";
        if (name.contains("DOOR")) return "a door";
        if (name.contains("BED") && mat != Material.BEDROCK) return "a bed";
        if (mat == Material.FARMLAND || mat == Material.WHEAT || mat == Material.CARROTS
                || mat == Material.POTATOES || mat == Material.BEETROOTS) return "farmland";
        return null;
    }

    private static String describeDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // Minecraft: -Z = north, +Z = south, +X = east (roughly, varies by coord system used by players)
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        } else {
            return dz > 0 ? "south" : "north";
        }
    }

    private static String summarizeEntities(List<String> entities) {
        if (entities.size() <= 4) return String.join(", ", entities);
        return entities.size() + " passive mobs nearby";
    }

    private static String formatBiomeName(String name) {
        return name.toLowerCase().replace('_', ' ');
    }

    private static String formatEntityName(String name) {
        return name.toLowerCase().replace('_', ' ');
    }

    private static String formatMaterialName(String name) {
        return name.toLowerCase().replace('_', ' ');
    }
}
