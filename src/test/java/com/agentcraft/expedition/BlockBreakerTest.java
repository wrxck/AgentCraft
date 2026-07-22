package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockBreakerTest {

    private TestWorld tw;
    private AIAgent agent;
    private BlockBreaker breaker;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        tw = new TestWorld();
        FakePlayer npc = mock(FakePlayer.class);
        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        breaker = new BlockBreaker(agent, new NPCGear());
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void breaksBlockAfterStagedTicksAndPreservesTiming() {
        tw.setType(0, 64, 0, Material.STONE);
        Location loc = new Location(tw.world, 0, 64, 0);

        int ticks = 0;
        while (!breaker.tickBreakBlock(loc)) {
            ticks++;
            assertTrue(ticks < 100, "block never broke");
        }
        // 10 stages (0..9) advanced every 3rd tick, then the breaking tick:
        // stage N is reached on tick 1+3N, so the final (breaking) call is
        // the 31st -> 30 false returns before it.
        assertEquals(30, ticks);
        assertEquals(Material.AIR, tw.getType(0, 64, 0));
    }

    @Test
    void resetRestartsProgress() {
        tw.setType(0, 64, 0, Material.STONE);
        Location loc = new Location(tw.world, 0, 64, 0);
        for (int i = 0; i < 10; i++) {
            assertFalse(breaker.tickBreakBlock(loc));
        }
        breaker.reset();
        int ticks = 0;
        while (!breaker.tickBreakBlock(loc)) {
            ticks++;
            assertTrue(ticks < 100, "block never broke");
        }
        assertEquals(30, ticks);
    }
}
