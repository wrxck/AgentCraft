package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.BukkitTestSupport;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.GiveItemTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GiveItemToolTest {

    private final GiveItemTool tool = new GiveItemTool();
    private final List<ItemStack> backing = new ArrayList<>();
    private final List<ItemStack> dropped = new ArrayList<>();
    private AIAgent agent;

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        agent = InventoryAgentMock.create(backing, world, new Location(world, 0, 0, 0));

        Player target = mock(Player.class);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(new Location(world, 2, 0, 2));

        when(world.dropItem(any(Location.class), any(ItemStack.class))).thenAnswer(inv -> {
            dropped.add(inv.getArgument(1));
            return null;
        });

        Server server = mock(Server.class);
        when(server.getPlayerExact("Steve")).thenReturn(target);
        BukkitTestSupport.setServer(server);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private int droppedTotal() {
        return dropped.stream().mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    void countZeroFailsWithoutDestroyingInventory() {
        backing.add(new ItemStack(Material.DIAMOND, 5));

        ToolResult result = tool.execute(agent, params("{\"player\":\"Steve\",\"count\":0}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("positive"),
                "expected count-must-be-positive message, got: " + result.message());
        assertEquals(1, backing.size(), "inventory must be untouched");
        assertEquals(5, backing.get(0).getAmount());
        assertTrue(dropped.isEmpty());
    }

    @Test
    void giveEverythingRemovesOnlyWhatWasDropped() {
        backing.add(new ItemStack(Material.COBBLESTONE, 10));
        backing.add(new ItemStack(Material.OAK_LOG, 8));

        ToolResult result = tool.execute(agent, params("{\"player\":\"Steve\",\"count\":12}"));

        assertTrue(result.success(), result.message());
        assertEquals(12, droppedTotal(), "exactly 12 items should be dropped");
        assertEquals(1, backing.size(), "undropped items must remain in inventory");
        assertEquals(Material.OAK_LOG, backing.get(0).getType());
        assertEquals(6, backing.get(0).getAmount());
    }

    @Test
    void giveEverythingWithNoLimitDropsAndClearsAll() {
        backing.add(new ItemStack(Material.COBBLESTONE, 10));
        backing.add(new ItemStack(Material.OAK_LOG, 8));

        ToolResult result = tool.execute(agent, params("{\"player\":\"Steve\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(18, droppedTotal());
        assertTrue(backing.isEmpty());
    }

    @Test
    void specificMaterialDropPreservesItemMeta() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.clone()).thenReturn(meta);
        sword.setItemMeta(meta);
        backing.add(sword);

        ToolResult result = tool.execute(agent,
                params("{\"player\":\"Steve\",\"material\":\"diamond_sword\",\"count\":1}"));

        assertTrue(result.success(), result.message());
        assertEquals(1, dropped.size());
        assertSame(meta, dropped.get(0).getItemMeta(),
                "dropped stack must carry the original item meta");
        assertTrue(backing.isEmpty());
    }
}
