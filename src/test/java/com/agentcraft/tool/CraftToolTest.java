package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.CraftTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CraftToolTest {

    private final CraftTool tool = new CraftTool();
    private final List<ItemStack> backing = new ArrayList<>();
    private AIAgent agent;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        agent = InventoryAgentMock.create(backing, world, new Location(world, 0, 0, 0));
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private int count(Material mat) {
        int total = 0;
        for (ItemStack stack : backing) {
            if (stack.getType() == mat) total += stack.getAmount();
        }
        return total;
    }

    @Test
    void craftsSticksFromMixedPlanksWithoutConcurrentModification() {
        backing.add(new ItemStack(Material.OAK_PLANKS, 1));
        backing.add(new ItemStack(Material.BIRCH_PLANKS, 1));

        ToolResult result = tool.execute(agent, params("{\"item\":\"stick\"}"));

        assertTrue(result.success(), "expected success but got: " + result.message());
        assertEquals(4, count(Material.STICK), "should have crafted 4 sticks");
        assertEquals(0, count(Material.OAK_PLANKS));
        assertEquals(0, count(Material.BIRCH_PLANKS));
    }

    @Test
    void breadDoesNotConsumeWheatSeeds() {
        backing.add(new ItemStack(Material.WHEAT_SEEDS, 3));

        ToolResult result = tool.execute(agent, params("{\"item\":\"bread\"}"));

        assertFalse(result.success(), "seeds are not wheat; craft must fail");
        assertEquals(3, count(Material.WHEAT_SEEDS), "seeds must not be consumed");
    }

    @Test
    void diamondSwordDoesNotConsumeDiamondPickaxe() {
        backing.add(new ItemStack(Material.DIAMOND_PICKAXE, 1));
        backing.add(new ItemStack(Material.DIAMOND, 2));
        backing.add(new ItemStack(Material.STICK, 1));

        ToolResult result = tool.execute(agent, params("{\"item\":\"diamond_sword\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, count(Material.DIAMOND_PICKAXE), "pickaxe must survive the craft");
        assertEquals(0, count(Material.DIAMOND));
        assertEquals(1, count(Material.DIAMOND_SWORD));
    }

    @Test
    void stoneStairsDoNotConsumeCobblestone() {
        backing.add(new ItemStack(Material.COBBLESTONE, 6));

        ToolResult result = tool.execute(agent, params("{\"item\":\"stone_stairs\"}"));

        assertFalse(result.success(), "cobblestone is not stone");
        assertEquals(6, count(Material.COBBLESTONE));
    }

    @Test
    void planksCategoryRecipesStillAcceptAnyPlanks() {
        backing.add(new ItemStack(Material.SPRUCE_PLANKS, 4));

        ToolResult result = tool.execute(agent, params("{\"item\":\"crafting_table\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, count(Material.CRAFTING_TABLE));
        assertEquals(0, count(Material.SPRUCE_PLANKS));
    }

    @Test
    void malformedCountGivesFriendlyFailure() {
        backing.add(new ItemStack(Material.OAK_LOG, 1));

        ToolResult result = tool.execute(agent, params("{\"item\":\"oak_planks\",\"count\":\"lots\"}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("count"),
                "expected friendly count message, got: " + result.message());
    }
}
