package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.StoreItemsTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StoreItemsToolTest {

    private final StoreItemsTool tool = new StoreItemsTool();
    private final List<ItemStack> backing = new ArrayList<>();
    private final List<ItemStack> chestContents = new ArrayList<>();
    private World world;
    private Inventory chestInv;
    private AIAgent agent;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        Location npcLoc = new Location(world, 0, 0, 0);
        agent = InventoryAgentMock.create(backing, world, npcLoc);

        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        chestInv = mock(Inventory.class);
        when(world.getBlockAt(1, 0, 1)).thenReturn(block);
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(chestInv);
        // Chest accepts everything (no overflow)
        when(chestInv.addItem(any(ItemStack.class))).thenAnswer(inv -> {
            chestContents.add(inv.getArgument(0));
            return new HashMap<Integer, ItemStack>();
        });
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void storesAllStacksWithoutConcurrentModification() {
        backing.add(new ItemStack(Material.COBBLESTONE, 32));
        backing.add(new ItemStack(Material.OAK_LOG, 5));

        ToolResult result = tool.execute(agent, params("{\"x\":1,\"y\":0,\"z\":1}"));

        assertTrue(result.success(), "expected success but got: " + result.message());
        assertEquals(2, chestContents.size(), "both stacks should reach the chest");
        assertTrue(backing.isEmpty(), "inventory should be emptied");
        assertTrue(result.message().contains("37"), "message should report 37 items: " + result.message());
    }

    @Test
    void depositedStacksPreserveItemMeta() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.clone()).thenReturn(meta);
        sword.setItemMeta(meta);
        backing.add(sword);

        ToolResult result = tool.execute(agent, params("{\"x\":1,\"y\":0,\"z\":1}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, chestContents.size());
        assertSame(meta, chestContents.get(0).getItemMeta(),
                "deposited stack must carry the original item meta");
    }

    @Test
    void malformedCoordinateGivesFriendlyFailure() {
        backing.add(new ItemStack(Material.COBBLESTONE, 1));

        ToolResult result = tool.execute(agent, params("{\"x\":\"abc\",\"y\":0,\"z\":1}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("coordinate"),
                "expected friendly coordinate message, got: " + result.message());
    }

    @Test
    void honorsCountLimit() {
        backing.add(new ItemStack(Material.COBBLESTONE, 32));

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"item\":\"cobblestone\",\"count\":10}"));

        assertTrue(result.success(), result.message());
        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        assertEquals(1, chestContents.size());
        assertEquals(10, chestContents.get(0).getAmount());
        assertEquals(1, backing.size());
        assertEquals(22, backing.get(0).getAmount());
    }
}
