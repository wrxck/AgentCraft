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

/**
 * Token-efficient spatial scanner producing compact ASCII grid + structured data.
 * Replaces verbose EnvironmentScanner with ~200 tokens vs ~400.
 */
public class CompactScanner {

    private static final int GRID_RADIUS = 10; // 21x21 grid
    private static final int XSEC_UP = 5;
    private static final int XSEC_DOWN = 3;
    private static final double ENTITY_SCAN_RADIUS = 12.0;

    private static final Map<Material, Character> BLOCK_CHARS = new HashMap<>();

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

    static {
        // Ground materials -> '.'
        for (Material m : Material.values()) {
            if (m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.SAND
                    || m == Material.GRAVEL || m == Material.DIRT_PATH || m == Material.PODZOL
                    || m == Material.COARSE_DIRT || m == Material.ROOTED_DIRT
                    || m == Material.RED_SAND || m == Material.CLAY
                    || m == Material.MUD || m == Material.MYCELIUM) {
                BLOCK_CHARS.put(m, '.');
            }
        }

        // Stone variants -> 'S'
        for (Material m : new Material[]{
                Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE,
                Material.COBBLED_DEEPSLATE, Material.ANDESITE, Material.DIORITE,
                Material.GRANITE, Material.TUFF, Material.CALCITE,
                Material.SMOOTH_STONE, Material.MOSSY_COBBLESTONE,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                Material.CRACKED_STONE_BRICKS, Material.POLISHED_ANDESITE,
                Material.POLISHED_DIORITE, Material.POLISHED_GRANITE,
                Material.POLISHED_DEEPSLATE, Material.BEDROCK
        }) {
            BLOCK_CHARS.put(m, 'S');
        }

        // Dynamic material classification by name
        for (Material m : Material.values()) {
            if (BLOCK_CHARS.containsKey(m)) continue;
            if (!m.isBlock()) continue;
            String n = m.name();

            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.equals("MUSHROOM_STEM")) {
                BLOCK_CHARS.put(m, 'T');
            } else if (n.endsWith("_LEAVES")) {
                BLOCK_CHARS.put(m, 'L');
            } else if (n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS")) {
                BLOCK_CHARS.put(m, 'O');
            } else if (n.endsWith("_PLANKS")) {
                BLOCK_CHARS.put(m, 'P');
            } else if (n.contains("GLASS") && !n.contains("GLASS_BOTTLE")) {
                BLOCK_CHARS.put(m, 'G');
            } else if (n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE")) {
                BLOCK_CHARS.put(m, 'N');
            } else if (n.endsWith("_DOOR") || n.equals("IRON_DOOR")) {
                BLOCK_CHARS.put(m, 'D');
            } else if (n.contains("BRICK") || n.contains("CONCRETE") || n.contains("TERRACOTTA")
                    || n.contains("PRISMARINE") || n.contains("PURPUR") || n.contains("QUARTZ_BLOCK")
                    || n.contains("SANDSTONE")) {
                BLOCK_CHARS.put(m, '#');
            } else if (n.endsWith("_BED") && !n.equals("BEDROCK")) {
                BLOCK_CHARS.put(m, 'B');
            }
        }

        // Specific blocks
        BLOCK_CHARS.put(Material.WATER, 'W');
        BLOCK_CHARS.put(Material.LAVA, '~');
        BLOCK_CHARS.put(Material.CHEST, 'C');
        BLOCK_CHARS.put(Material.TRAPPED_CHEST, 'C');
        BLOCK_CHARS.put(Material.BARREL, 'C');
        BLOCK_CHARS.put(Material.FURNACE, 'F');
        BLOCK_CHARS.put(Material.BLAST_FURNACE, 'F');
        BLOCK_CHARS.put(Material.SMOKER, 'F');
        BLOCK_CHARS.put(Material.CRAFTING_TABLE, '+');
        BLOCK_CHARS.put(Material.TORCH, '!');
        BLOCK_CHARS.put(Material.WALL_TORCH, '!');
        BLOCK_CHARS.put(Material.SOUL_TORCH, '!');
        BLOCK_CHARS.put(Material.SOUL_WALL_TORCH, '!');
        BLOCK_CHARS.put(Material.LANTERN, '!');
        BLOCK_CHARS.put(Material.SOUL_LANTERN, '!');
        BLOCK_CHARS.put(Material.FARMLAND, '_');
        BLOCK_CHARS.put(Material.WHEAT, '*');
        BLOCK_CHARS.put(Material.CARROTS, '*');
        BLOCK_CHARS.put(Material.POTATOES, '*');
        BLOCK_CHARS.put(Material.BEETROOTS, '*');
        BLOCK_CHARS.put(Material.MELON, '*');
        BLOCK_CHARS.put(Material.PUMPKIN, '*');
        BLOCK_CHARS.put(Material.SWEET_BERRY_BUSH, 'H');
        BLOCK_CHARS.put(Material.CACTUS, 'H');
        BLOCK_CHARS.put(Material.MAGMA_BLOCK, 'H');
    }

    /**
     * Scan result that can be cached for delta comparison.
     */
    public static class ScanResult {
        public final String pos;
        public final String map;
        public final String xsec;
        public final String entities;
        public final String inv;
        public final String blocks;
        public final String full;

        ScanResult(String pos, String map, String xsec, String entities, String inv, String blocks) {
            this.pos = pos;
            this.map = map;
            this.xsec = xsec;
            this.entities = entities;
            this.inv = inv;
            this.blocks = blocks;

            StringBuilder sb = new StringBuilder();
            sb.append("[ENV]\n").append(pos).append('\n');
            sb.append(map);
            sb.append(xsec);
            if (!entities.isEmpty()) sb.append("[ENTITIES]\n").append(entities).append('\n');
            if (!inv.isEmpty()) sb.append("[INV]\n").append(inv).append('\n');
            if (!blocks.isEmpty()) sb.append("[BLOCKS]\n").append(blocks).append('\n');
            this.full = sb.toString();
        }
    }

    public static ScanResult scan(AIAgent agent) {
        Location loc = agent.getNpc().getLocation();
        World world = loc.getWorld();
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        String pos = buildPosLine(agent, loc, world);
        String map = buildMap(world, cx, cy, cz, agent);
        String xsec = buildXsec(world, cx, cy, cz, agent);
        String entities = buildEntities(agent, loc, world);
        String inv = buildInventory(agent);
        String blocks = buildNotableBlocks(world, cx, cy, cz);

        return new ScanResult(pos, map, xsec, entities, inv, blocks);
    }

    /**
     * Produce a delta update containing only sections that changed.
     */
    public static String scanDelta(AIAgent agent, ScanResult previous) {
        ScanResult current = scan(agent);
        StringBuilder sb = new StringBuilder();
        sb.append("[ENV_DELTA]\n");

        // Always include position
        sb.append(current.pos).append('\n');

        // Only include changed sections
        if (!current.entities.equals(previous.entities) && !current.entities.isEmpty()) {
            sb.append("[ENTITIES]\n").append(current.entities).append('\n');
        }
        if (!current.inv.equals(previous.inv) && !current.inv.isEmpty()) {
            sb.append("[INV]\n").append(current.inv).append('\n');
        }

        return sb.toString();
    }

    private static String buildPosLine(AIAgent agent, Location loc, World world) {
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

        String sky;
        if (world.isThundering()) sky = "storm";
        else if (world.hasStorm()) sky = "rain";
        else sky = "clear";

        Location home = agent.getBehaviorController().getHomeLocation();
        String stateStr = agent.getState().name().toLowerCase();

        return "pos:" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + " biome:" + biomeName + " time:" + timeDesc + " sky:" + sky
                + " home:" + home.getBlockX() + "," + home.getBlockY() + "," + home.getBlockZ()
                + " state:" + stateStr;
    }

    private static String buildMap(World world, int cx, int cy, int cz, AIAgent agent) {
        int size = GRID_RADIUS * 2 + 1;
        char[][] grid = new char[size][size];

        // Collect entity positions for overlay
        Map<String, int[]> entityOverlay = getEntityOverlay(agent, cx, cz);

        for (int dz = -GRID_RADIUS; dz <= GRID_RADIUS; dz++) {
            for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++) {
                int gx = dx + GRID_RADIUS;
                int gz = dz + GRID_RADIUS;

                Block block = world.getBlockAt(cx + dx, cy, cz + dz);
                char ch = getBlockChar(block);

                // If air at NPC level, check ground below
                if (ch == ' ') {
                    Block below = world.getBlockAt(cx + dx, cy - 1, cz + dz);
                    ch = getBlockChar(below);
                    if (ch == ' ') ch = '.'; // default to ground
                }

                grid[gz][gx] = ch;
            }
        }

        // Overlay self
        grid[GRID_RADIUS][GRID_RADIUS] = '@';

        // Overlay entities
        for (Map.Entry<String, int[]> entry : entityOverlay.entrySet()) {
            int[] offsets = entry.getValue();
            int gx = offsets[0] + GRID_RADIUS;
            int gz = offsets[1] + GRID_RADIUS;
            if (gx >= 0 && gx < size && gz >= 0 && gz < size) {
                char entityChar = entry.getKey().charAt(0);
                grid[gz][gx] = entityChar;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[MAP:").append(size).append("x").append(size).append(":y").append(cy).append("]\n");
        for (int z = 0; z < size; z++) {
            sb.append(new String(grid[z])).append('\n');
        }
        return sb.toString();
    }

    private static String buildXsec(World world, int cx, int cy, int cz, AIAgent agent) {
        int width = GRID_RADIUS * 2 + 1;
        int height = XSEC_UP + XSEC_DOWN + 1; // 9 rows
        char[][] grid = new char[height][width];

        for (int dy = XSEC_UP; dy >= -XSEC_DOWN; dy--) {
            int row = XSEC_UP - dy;
            for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++) {
                int col = dx + GRID_RADIUS;
                Block block = world.getBlockAt(cx + dx, cy + dy, cz);
                grid[row][col] = getBlockChar(block);
            }
        }

        // Overlay self at center (y=0)
        grid[XSEC_UP][GRID_RADIUS] = '@';

        StringBuilder sb = new StringBuilder();
        sb.append("[XSEC:").append(width).append("x").append(height).append(":z").append(cz).append("]\n");
        for (int row = 0; row < height; row++) {
            sb.append(new String(grid[row])).append('\n');
        }
        return sb.toString();
    }

    private static String buildEntities(AIAgent agent, Location loc, World world) {
        List<Entity> nearby = new ArrayList<>(
                world.getNearbyEntities(loc, ENTITY_SCAN_RADIUS, ENTITY_SCAN_RADIUS, ENTITY_SCAN_RADIUS));

        Map<String, List<String>> hostiles = new LinkedHashMap<>();
        Map<String, List<String>> passives = new LinkedHashMap<>();
        List<String> players = new ArrayList<>();

        for (Entity entity : nearby) {
            int dist = (int) Math.round(entity.getLocation().distance(loc));
            String dir = compactDirection(loc, entity.getLocation());

            if (entity instanceof Player player) {
                players.add(player.getName() + ":" + dist + dir);
            } else if (HOSTILE_MOBS.contains(entity.getType())) {
                String name = entity.getType().name().toLowerCase();
                hostiles.computeIfAbsent(name, k -> new ArrayList<>()).add(dist + dir);
            } else if (PASSIVE_MOBS.contains(entity.getType())) {
                String name = entity.getType().name().toLowerCase();
                passives.computeIfAbsent(name, k -> new ArrayList<>()).add(dist + dir);
            }
        }

        // Other NPCs
        for (AIAgent other : AgentManager.getInstance().getAllAgents()) {
            if (other == agent) continue;
            Location otherLoc = other.getNpc().getLocation();
            if (!otherLoc.getWorld().equals(world)) continue;
            double dist = otherLoc.distance(loc);
            if (dist <= ENTITY_SCAN_RADIUS) {
                String dir = compactDirection(loc, otherLoc);
                players.add("npc:" + other.getNpc().getName() + ":" + (int) Math.round(dist) + dir);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : hostiles.entrySet()) {
            sb.append(entry.getKey()).append(':').append(String.join(",", entry.getValue())).append(' ');
        }
        for (Map.Entry<String, List<String>> entry : passives.entrySet()) {
            sb.append(entry.getKey()).append(':').append(String.join(",", entry.getValue())).append(' ');
        }
        for (String p : players) {
            sb.append("player:").append(p).append(' ');
        }

        return sb.toString().trim();
    }

    private static String buildInventory(AIAgent agent) {
        List<ItemStack> inventory = agent.getBehaviorController().getInventory();
        if (inventory.isEmpty()) return "";

        Map<String, Integer> items = new LinkedHashMap<>();
        for (ItemStack stack : inventory) {
            String name = stack.getType().name().toLowerCase();
            items.merge(name, stack.getAmount(), Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String buildNotableBlocks(World world, int cx, int cy, int cz) {
        StringBuilder sb = new StringBuilder();
        int radius = 8;
        int count = 0;

        for (int dx = -radius; dx <= radius && count < 8; dx++) {
            for (int dy = -4; dy <= 4 && count < 8; dy++) {
                for (int dz = -radius; dz <= radius && count < 8; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material mat = block.getType();
                    if (isNotable(mat)) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(mat.name().toLowerCase()).append(':')
                                .append(cx + dx).append(',').append(cy + dy).append(',').append(cz + dz);
                        count++;
                    }
                }
            }
        }

        return sb.toString();
    }

    private static boolean isNotable(Material mat) {
        return mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL
                || mat == Material.CRAFTING_TABLE || mat == Material.FURNACE
                || mat == Material.BLAST_FURNACE || mat == Material.SMOKER
                || mat == Material.ANVIL || mat == Material.ENCHANTING_TABLE
                || mat == Material.BREWING_STAND
                || mat.name().endsWith("_ORE") || mat == Material.ANCIENT_DEBRIS;
    }

    private static char getBlockChar(Block block) {
        return charForMaterial(block.getType());
    }

    // Package-private for tests.
    static char charForMaterial(Material mat) {
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
            return ' ';
        }
        Character ch = BLOCK_CHARS.get(mat);
        return ch != null ? ch : (mat.isSolid() ? 'S' : ' ');
    }

    private static Map<String, int[]> getEntityOverlay(AIAgent agent, int cx, int cz) {
        Location loc = agent.getNpc().getLocation();
        World world = loc.getWorld();
        Map<String, int[]> overlay = new LinkedHashMap<>();

        int idx = 0;
        for (Entity entity : world.getNearbyEntities(loc, GRID_RADIUS, GRID_RADIUS, GRID_RADIUS)) {
            int dx = entity.getLocation().getBlockX() - cx;
            int dz = entity.getLocation().getBlockZ() - cz;
            if (Math.abs(dx) > GRID_RADIUS || Math.abs(dz) > GRID_RADIUS) continue;
            if (dx == 0 && dz == 0) continue; // self position

            char ch;
            if (entity instanceof Player) {
                ch = 'p';
            } else if (HOSTILE_MOBS.contains(entity.getType())) {
                ch = 'm';
            } else if (PASSIVE_MOBS.contains(entity.getType())) {
                ch = 'a';
            } else {
                continue;
            }
            overlay.put(ch + "_" + (idx++), new int[]{dx, dz});
        }

        // Other NPCs
        for (AIAgent other : AgentManager.getInstance().getAllAgents()) {
            if (other == agent) continue;
            Location otherLoc = other.getNpc().getLocation();
            if (!otherLoc.getWorld().equals(world)) continue;
            int dx = otherLoc.getBlockX() - cx;
            int dz = otherLoc.getBlockZ() - cz;
            if (Math.abs(dx) <= GRID_RADIUS && Math.abs(dz) <= GRID_RADIUS) {
                overlay.put("p_npc_" + (idx++), new int[]{dx, dz});
            }
        }

        return overlay;
    }

    private static String compactDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        StringBuilder dir = new StringBuilder();
        if (Math.abs(dz) > 1) dir.append(dz < 0 ? "N" : "S");
        if (Math.abs(dx) > 1) dir.append(dx > 0 ? "E" : "W");

        return dir.toString();
    }
}
