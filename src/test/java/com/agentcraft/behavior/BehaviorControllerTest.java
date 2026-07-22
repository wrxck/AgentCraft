package com.agentcraft.behavior;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.agentcraft.AgentCraftPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BehaviorControllerTest {

    private Server server;
    private World world;
    private AIAgent agent;
    private FakePlayer npc;
    private BehaviorController controller;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
        // Mirror real Bukkit: getPlayer(null) throws.
        when(server.getPlayer(nullable(UUID.class))).thenAnswer(inv -> {
            if (inv.getArgument(0) == null) {
                throw new NullPointerException("UUID cannot be null");
            }
            return null;
        });
        BukkitTestSupport.setServer(server);

        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(60);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(materialAt(x, y, z));
            when(block.getLocation()).thenReturn(new Location(world, x, y, z));
            return block;
        });

        npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn("Bob");
        when(npc.getLocation()).thenReturn(new Location(world, 0.5, 65, 0.5));

        AgentCraftPlugin plugin = mock(AgentCraftPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("BehaviorControllerTest"));

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        doReturn(plugin).when(agent).getPlugin();

        controller = new BehaviorController(agent);
    }

    // Sparse block map used by findNearestBlockByMaterial tests.
    private Material materialAt(int x, int y, int z) {
        if (x == 2 && y == 65 && z == 2) return Material.REDSTONE_ORE;
        if (x == 5 && y == 65 && z == 5) return Material.STONE;
        return Material.AIR;
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private Object readField(String name) throws Exception {
        Field field = BehaviorController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    @Test
    void expeditionWithoutRequesterDoesNotThrow() {
        // No task requester set and no players online: the expedition parse
        // must fall through cleanly instead of calling Bukkit.getPlayer(null).
        assertDoesNotThrow(() -> controller.executeAction("expedition iron_ore 4"));
        verify(server, never()).getPlayer(nullable(UUID.class));
    }

    @Test
    void expeditionWithOfflineRequesterFallsBackWithoutThrowing() {
        UUID requester = UUID.randomUUID();
        controller.setTaskRequester(requester);
        assertDoesNotThrow(() -> controller.executeAction("expedition iron_ore 4"));
        verify(server).getPlayer(requester);
    }

    @Test
    void startFleeingClearsFollowTarget() throws Exception {
        Player followed = mock(Player.class);
        controller.startFollowing(followed);
        assertSame(followed, readField("followTarget"));

        // "flee" with no threats picks a random flee direction and starts fleeing.
        controller.executeAction("flee");

        assertNull(readField("followTarget"),
                "fleeing must clear followTarget or the agent stays busy forever after the flee ends");
    }

    @Test
    void findNearestBlockByMaterialUsesTokenMatching() {
        // REDSTONE_ORE at (2,65,2) is nearer than STONE at (5,65,5): a substring
        // match would wrongly pick REDSTONE_ORE for target "STONE".
        Location found = controller.findNearestBlockByMaterial("STONE");
        assertNotNull(found);
        assertEquals(5, found.getBlockX());
        assertEquals(65, found.getBlockY());
        assertEquals(5, found.getBlockZ());
    }

    @Test
    void findNearestBlockByMaterialStillFindsExactAndNearest() {
        Location found = controller.findNearestBlockByMaterial("REDSTONE_ORE");
        assertNotNull(found);
        assertEquals(2, found.getBlockX());
        assertEquals(2, found.getBlockZ());

        assertNull(controller.findNearestBlockByMaterial("DIAMOND_ORE"));
    }

    @Test
    void materialCountParserHandlesCountAndDefaults() {
        BehaviorController.MaterialRequest withCount = BehaviorController.parseMaterialCount("iron_ore 32");
        assertEquals("iron_ore", withCount.material());
        assertEquals(32, withCount.count());

        BehaviorController.MaterialRequest noCount = BehaviorController.parseMaterialCount("log");
        assertEquals("log", noCount.material());
        assertEquals(16, noCount.count());

        BehaviorController.MaterialRequest spaced = BehaviorController.parseMaterialCount("oak log 10");
        assertEquals("oak_log", spaced.material());
        assertEquals(10, spaced.count());

        BehaviorController.MaterialRequest badCount = BehaviorController.parseMaterialCount("oak log");
        assertEquals("oak_log", badCount.material());
        assertEquals(16, badCount.count());
    }
}
