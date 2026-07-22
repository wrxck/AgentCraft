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
import com.agentcraft.tool.impl.InteractBlockTool;
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

class InteractBlockToolTest {

    private final InteractBlockTool tool = new InteractBlockTool();
    private final List<ItemStack> backing = new ArrayList<>();
    private final List<ItemStack> chestDeposits = new ArrayList<>();
    private AIAgent agent;
    private Inventory chestInv;
    private ItemStack[] chestSlots;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        agent = InventoryAgentMock.create(backing, world, new Location(world, 0, 0, 0));

        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        chestInv = mock(Inventory.class);
        when(world.getBlockAt(1, 0, 1)).thenReturn(block);
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(chestInv);

        chestSlots = new ItemStack[27];
        when(chestInv.getSize()).thenReturn(27);
        when(chestInv.getItem(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> chestSlots[(int) inv.getArgument(0)]);
        org.mockito.Mockito.doAnswer(inv -> {
            chestSlots[(int) inv.getArgument(0)] = inv.getArgument(1);
            return null;
        }).when(chestInv).setItem(org.mockito.ArgumentMatchers.anyInt(), any());
        when(chestInv.addItem(any(ItemStack.class))).thenAnswer(inv -> {
            chestDeposits.add(inv.getArgument(0));
            return new HashMap<Integer, ItemStack>();
        });
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private int backingCount(Material mat) {
        int total = 0;
        for (ItemStack stack : backing) {
            if (stack.getType() == mat) total += stack.getAmount();
        }
        return total;
    }

    @Test
    void takeHonorsItemFilterAndCount() {
        chestSlots[0] = new ItemStack(Material.BREAD, 3);
        chestSlots[1] = new ItemStack(Material.STONE, 5);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"action\":\"take\",\"item\":\"bread\",\"count\":1}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, backingCount(Material.BREAD), "should take exactly 1 bread");
        assertEquals(0, backingCount(Material.STONE), "stone must stay in the chest");
        assertEquals(2, chestSlots[0].getAmount(), "2 bread must remain in the chest");
        assertEquals(5, chestSlots[1].getAmount(), "stone stack untouched");
    }

    @Test
    void takeWithoutFilterStillTakesEverything() {
        chestSlots[0] = new ItemStack(Material.BREAD, 3);
        chestSlots[1] = new ItemStack(Material.STONE, 5);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"action\":\"take\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(3, backingCount(Material.BREAD));
        assertEquals(5, backingCount(Material.STONE));
    }

    @Test
    void depositPreservesItemMeta() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.clone()).thenReturn(meta);
        sword.setItemMeta(meta);
        backing.add(sword);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"action\":\"deposit\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, chestDeposits.size());
        assertSame(meta, chestDeposits.get(0).getItemMeta(),
                "deposited stack must carry the original item meta");
        assertTrue(backing.isEmpty());
    }

    @Test
    void depositMultipleStacksWorks() {
        backing.add(new ItemStack(Material.COBBLESTONE, 4));
        backing.add(new ItemStack(Material.DIRT, 2));

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"action\":\"deposit\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(2, chestDeposits.size());
        assertTrue(backing.isEmpty());
    }

    @Test
    void malformedCoordinateGivesFriendlyFailure() {
        ToolResult result = tool.execute(agent,
                params("{\"x\":\"abc\",\"y\":0,\"z\":1,\"action\":\"read\"}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("coordinate"),
                "expected friendly coordinate message, got: " + result.message());
    }
}
