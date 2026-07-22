package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolArgs;
import com.agentcraft.tool.ToolResult;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class CraftTool implements MinecraftTool {

    // Simplified recipe definitions: input materials -> output
    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        // Planks from logs
        RECIPES.put("oak_planks", new Recipe(Material.OAK_PLANKS, 4, mat("OAK_LOG", 1)));
        RECIPES.put("birch_planks", new Recipe(Material.BIRCH_PLANKS, 4, mat("BIRCH_LOG", 1)));
        RECIPES.put("spruce_planks", new Recipe(Material.SPRUCE_PLANKS, 4, mat("SPRUCE_LOG", 1)));
        RECIPES.put("dark_oak_planks", new Recipe(Material.DARK_OAK_PLANKS, 4, mat("DARK_OAK_LOG", 1)));

        // Sticks from planks (any planks)
        RECIPES.put("stick", new Recipe(Material.STICK, 4, mat("_PLANKS", 2)));

        // Crafting table
        RECIPES.put("crafting_table", new Recipe(Material.CRAFTING_TABLE, 1, mat("_PLANKS", 4)));

        // Torch
        RECIPES.put("torch", new Recipe(Material.TORCH, 4, mat("STICK", 1), mat("COAL", 1)));

        // Wooden tools
        RECIPES.put("wooden_pickaxe", new Recipe(Material.WOODEN_PICKAXE, 1, mat("_PLANKS", 3), mat("STICK", 2)));
        RECIPES.put("wooden_sword", new Recipe(Material.WOODEN_SWORD, 1, mat("_PLANKS", 2), mat("STICK", 1)));
        RECIPES.put("wooden_axe", new Recipe(Material.WOODEN_AXE, 1, mat("_PLANKS", 3), mat("STICK", 2)));
        RECIPES.put("wooden_shovel", new Recipe(Material.WOODEN_SHOVEL, 1, mat("_PLANKS", 1), mat("STICK", 2)));

        // Stone tools
        RECIPES.put("stone_pickaxe", new Recipe(Material.STONE_PICKAXE, 1, mat("COBBLESTONE", 3), mat("STICK", 2)));
        RECIPES.put("stone_sword", new Recipe(Material.STONE_SWORD, 1, mat("COBBLESTONE", 2), mat("STICK", 1)));
        RECIPES.put("stone_axe", new Recipe(Material.STONE_AXE, 1, mat("COBBLESTONE", 3), mat("STICK", 2)));

        // Furnace
        RECIPES.put("furnace", new Recipe(Material.FURNACE, 1, mat("COBBLESTONE", 8)));

        // Chest
        RECIPES.put("chest", new Recipe(Material.CHEST, 1, mat("_PLANKS", 8)));

        // Ladder
        RECIPES.put("ladder", new Recipe(Material.LADDER, 3, mat("STICK", 7)));

        // Fence
        RECIPES.put("oak_fence", new Recipe(Material.OAK_FENCE, 3, mat("_PLANKS", 4), mat("STICK", 2)));

        // Door
        RECIPES.put("oak_door", new Recipe(Material.OAK_DOOR, 3, mat("_PLANKS", 6)));

        // Iron tools
        RECIPES.put("iron_pickaxe", new Recipe(Material.IRON_PICKAXE, 1, mat("IRON_INGOT", 3), mat("STICK", 2)));
        RECIPES.put("iron_sword", new Recipe(Material.IRON_SWORD, 1, mat("IRON_INGOT", 2), mat("STICK", 1)));
        RECIPES.put("iron_axe", new Recipe(Material.IRON_AXE, 1, mat("IRON_INGOT", 3), mat("STICK", 2)));
        RECIPES.put("iron_shovel", new Recipe(Material.IRON_SHOVEL, 1, mat("IRON_INGOT", 1), mat("STICK", 2)));
        RECIPES.put("iron_hoe", new Recipe(Material.IRON_HOE, 1, mat("IRON_INGOT", 2), mat("STICK", 2)));

        // Diamond tools
        RECIPES.put("diamond_pickaxe", new Recipe(Material.DIAMOND_PICKAXE, 1, mat("DIAMOND", 3), mat("STICK", 2)));
        RECIPES.put("diamond_sword", new Recipe(Material.DIAMOND_SWORD, 1, mat("DIAMOND", 2), mat("STICK", 1)));
        RECIPES.put("diamond_axe", new Recipe(Material.DIAMOND_AXE, 1, mat("DIAMOND", 3), mat("STICK", 2)));
        RECIPES.put("diamond_shovel", new Recipe(Material.DIAMOND_SHOVEL, 1, mat("DIAMOND", 1), mat("STICK", 2)));
        RECIPES.put("diamond_hoe", new Recipe(Material.DIAMOND_HOE, 1, mat("DIAMOND", 2), mat("STICK", 2)));

        // Iron armor
        RECIPES.put("iron_helmet", new Recipe(Material.IRON_HELMET, 1, mat("IRON_INGOT", 5)));
        RECIPES.put("iron_chestplate", new Recipe(Material.IRON_CHESTPLATE, 1, mat("IRON_INGOT", 8)));
        RECIPES.put("iron_leggings", new Recipe(Material.IRON_LEGGINGS, 1, mat("IRON_INGOT", 7)));
        RECIPES.put("iron_boots", new Recipe(Material.IRON_BOOTS, 1, mat("IRON_INGOT", 4)));

        // Diamond armor
        RECIPES.put("diamond_helmet", new Recipe(Material.DIAMOND_HELMET, 1, mat("DIAMOND", 5)));
        RECIPES.put("diamond_chestplate", new Recipe(Material.DIAMOND_CHESTPLATE, 1, mat("DIAMOND", 8)));
        RECIPES.put("diamond_leggings", new Recipe(Material.DIAMOND_LEGGINGS, 1, mat("DIAMOND", 7)));
        RECIPES.put("diamond_boots", new Recipe(Material.DIAMOND_BOOTS, 1, mat("DIAMOND", 4)));

        // Utility items
        RECIPES.put("bucket", new Recipe(Material.BUCKET, 1, mat("IRON_INGOT", 3)));
        RECIPES.put("shears", new Recipe(Material.SHEARS, 1, mat("IRON_INGOT", 2)));
        RECIPES.put("shield", new Recipe(Material.SHIELD, 1, mat("_PLANKS", 6), mat("IRON_INGOT", 1)));
        RECIPES.put("bow", new Recipe(Material.BOW, 1, mat("STICK", 3), mat("STRING", 3)));
        RECIPES.put("arrow", new Recipe(Material.ARROW, 4, mat("STICK", 1), mat("FLINT", 1), mat("FEATHER", 1)));
        RECIPES.put("boat", new Recipe(Material.OAK_BOAT, 1, mat("_PLANKS", 5)));

        // Hoes (wood/stone)
        RECIPES.put("wooden_hoe", new Recipe(Material.WOODEN_HOE, 1, mat("_PLANKS", 2), mat("STICK", 2)));
        RECIPES.put("stone_hoe", new Recipe(Material.STONE_HOE, 1, mat("COBBLESTONE", 2), mat("STICK", 2)));

        // Bed
        RECIPES.put("bed", new Recipe(Material.WHITE_BED, 1, mat("_PLANKS", 3), mat("_WOOL", 3)));

        // Food
        RECIPES.put("bread", new Recipe(Material.BREAD, 1, mat("WHEAT", 3)));

        // Building blocks
        RECIPES.put("stone_stairs", new Recipe(Material.STONE_STAIRS, 4, mat("STONE", 6)));
        RECIPES.put("cobblestone_stairs", new Recipe(Material.COBBLESTONE_STAIRS, 4, mat("COBBLESTONE", 6)));
        RECIPES.put("oak_stairs", new Recipe(Material.OAK_STAIRS, 4, mat("OAK_PLANKS", 6)));
        RECIPES.put("stone_slab", new Recipe(Material.STONE_SLAB, 6, mat("STONE", 3)));
        RECIPES.put("cobblestone_slab", new Recipe(Material.COBBLESTONE_SLAB, 6, mat("COBBLESTONE", 3)));
        RECIPES.put("oak_slab", new Recipe(Material.OAK_SLAB, 6, mat("OAK_PLANKS", 3)));

        // Stone tools (shovel was missing)
        RECIPES.put("stone_shovel", new Recipe(Material.STONE_SHOVEL, 1, mat("COBBLESTONE", 1), mat("STICK", 2)));
    }

    @Override public String getName() { return "craft"; }

    @Override public String getDescription() {
        return "Craft an item using materials from your inventory. Supports common recipes: planks, sticks, tools, torch, furnace, chest, etc.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("item", "string: item to craft (e.g. oak_planks, stick, wooden_pickaxe, torch, furnace, chest)");
        schema.addProperty("count", "integer: how many batches to craft (default 1)");
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
        String rawItem = ToolArgs.optString(params, "item");
        String itemName = rawItem != null ? rawItem.toLowerCase().replace(' ', '_') : null;
        if (itemName == null || itemName.isEmpty()) return ToolResult.fail("No item specified");

        int batches = ToolArgs.optInt(params, "count", 1);
        batches = Math.max(1, Math.min(batches, 64));

        Recipe recipe = RECIPES.get(itemName);
        if (recipe == null) {
            StringBuilder available = new StringBuilder("Unknown recipe. Available: ");
            RECIPES.keySet().forEach(k -> available.append(k).append(", "));
            available.setLength(available.length() - 2);
            return ToolResult.fail(available.toString());
        }

        // Check and consume ingredients for each batch
        int crafted = 0;
        for (int b = 0; b < batches; b++) {
            if (!hasIngredients(agent, recipe)) break;
            consumeIngredients(agent, recipe);
            agent.getBehaviorController().addToInventory(new ItemStack(recipe.output, recipe.outputCount));
            crafted++;
        }

        if (crafted == 0) {
            StringBuilder needed = new StringBuilder("Missing materials. Need: ");
            for (Ingredient ing : recipe.ingredients) {
                needed.append(ing.count).append("x ").append(ing.pattern.toLowerCase().replace('_', ' ')).append(", ");
            }
            needed.setLength(needed.length() - 2);
            return ToolResult.fail(needed.toString());
        }

        int totalItems = crafted * recipe.outputCount;
        return ToolResult.ok("Crafted " + totalItems + "x "
                + recipe.output.name().toLowerCase().replace('_', ' '));
    }

    private boolean hasIngredients(AIAgent agent, Recipe recipe) {
        for (Ingredient ing : recipe.ingredients) {
            int available = countMatching(agent, ing.pattern);
            if (available < ing.count) return false;
        }
        return true;
    }

    private void consumeIngredients(AIAgent agent, Recipe recipe) {
        for (Ingredient ing : recipe.ingredients) {
            int remaining = ing.count;
            // Iterate a snapshot copy: removeFromInventory structurally mutates
            // the live list backing getInventory(), which would otherwise abort
            // or corrupt iteration (CME / partially-consumed ingredients).
            for (ItemStack stack : new ArrayList<>(agent.getBehaviorController().getInventory())) {
                if (remaining <= 0) break;
                if (matchesMaterial(stack.getType(), ing.pattern)) {
                    int remove = Math.min(remaining, stack.getAmount());
                    int removed = agent.getBehaviorController().removeFromInventory(stack.getType(), remove);
                    remaining -= removed;
                }
            }
        }
    }

    private int countMatching(AIAgent agent, String pattern) {
        int total = 0;
        for (ItemStack stack : agent.getBehaviorController().getInventory()) {
            if (matchesMaterial(stack.getType(), pattern)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Recipe patterns are either an exact material name (e.g. "WHEAT", "DIAMOND")
     * or a suffix category starting with "_" (e.g. "_PLANKS" matches any planks).
     * Free substring matching is deliberately NOT used: it made "WHEAT" consume
     * WHEAT_SEEDS, "DIAMOND" consume DIAMOND_PICKAXE, "STONE" consume
     * COBBLESTONE/REDSTONE, etc.
     */
    private boolean matchesMaterial(Material mat, String pattern) {
        String name = mat.name();
        if (pattern.startsWith("_")) {
            return name.endsWith(pattern);
        }
        return name.equalsIgnoreCase(pattern);
    }

    private static Ingredient mat(String pattern, int count) {
        return new Ingredient(pattern, count);
    }

    private record Ingredient(String pattern, int count) {}
    private record Recipe(Material output, int outputCount, Ingredient... ingredients) {}
}
