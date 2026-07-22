package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.AgentEquipment;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.EquipTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EquipToolTest {

    private final EquipTool tool = new EquipTool();
    private final List<ItemStack> backing = new ArrayList<>();
    private AIAgent agent;
    private FakePlayer npc;
    private final AtomicReference<AgentEquipment> equipmentModel = new AtomicReference<>(new AgentEquipment());

    @BeforeEach
    void setUp() {
        World world = mock(World.class);
        agent = InventoryAgentMock.create(backing, world, new Location(world, 0, 0, 0));
        npc = agent.getNpc();
        when(npc.getEquipment()).thenAnswer(inv -> equipmentModel.get());
        doAnswer(inv -> {
            equipmentModel.set(inv.getArgument(0));
            return null;
        }).when(npc).setEquipment(any(AgentEquipment.class));

        BukkitTestSupport.setServer(mock(Server.class));
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void equipUpdatesEquipmentModelSoRespawnResendsIt() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.clone()).thenReturn(meta);
        sword.setItemMeta(meta);
        backing.add(sword);

        ToolResult result = tool.execute(agent, params("{\"slot\":\"hand\",\"material\":\"iron_sword\"}"));

        assertTrue(result.success(), result.message());
        AgentEquipment model = equipmentModel.get();
        assertNotNull(model);
        assertEquals(Material.IRON_SWORD, model.getMainHand().getType(),
                "equipment model must reflect the equipped item so respawn re-sends it");
        assertSame(meta, model.getMainHand().getItemMeta(),
                "equipped item must keep its meta");
        assertTrue(backing.isEmpty(), "item should leave the inventory");
    }

    @Test
    void replacingEquippedItemReturnsOldOneToInventory() {
        backing.add(new ItemStack(Material.IRON_SWORD, 1));
        backing.add(new ItemStack(Material.STONE_SWORD, 1));

        ToolResult first = tool.execute(agent, params("{\"slot\":\"hand\",\"material\":\"iron_sword\"}"));
        assertTrue(first.success(), first.message());

        ToolResult second = tool.execute(agent, params("{\"slot\":\"hand\",\"material\":\"stone_sword\"}"));
        assertTrue(second.success(), second.message());

        assertEquals(Material.STONE_SWORD, equipmentModel.get().getMainHand().getType());
        assertEquals(1, backing.size(), "previously equipped item must return to inventory");
        assertEquals(Material.IRON_SWORD, backing.get(0).getType());
    }

    @Test
    void armorSlotUpdatesModel() {
        backing.add(new ItemStack(Material.IRON_HELMET, 1));

        ToolResult result = tool.execute(agent, params("{\"slot\":\"head\",\"material\":\"iron_helmet\"}"));

        assertTrue(result.success(), result.message());
        assertEquals(Material.IRON_HELMET, equipmentModel.get().getHelmet().getType());
    }

    @Test
    void nullMaterialGivesFriendlyFailure() {
        ToolResult result = tool.execute(agent, params("{\"slot\":\"hand\",\"material\":null}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("material"),
                "expected friendly message, got: " + result.message());
    }
}
