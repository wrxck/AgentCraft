package com.agentcraft.navigation;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationControllerTest {

    private GridWorld grid;
    private AIAgent agent;
    private FakePlayer npc;
    private NavigationController nav;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(Collections.emptyList());
        BukkitTestSupport.setServer(server);

        grid = new GridWorld();
        grid.plane(-2, 12, -2, 12, 65);

        npc = new FakePlayer(mock(Plugin.class), "NavBot", new Location(grid.world, 0.5, 65, 0.5));
        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        nav = new NavigationController(agent);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private void tickUntil(java.util.function.BooleanSupplier done, int maxTicks) {
        for (int i = 0; i < maxTicks && !done.getAsBoolean(); i++) {
            nav.tick();
        }
    }

    @Test
    void simpleNavigationArrivesAndInvokesCallback() {
        boolean[] arrived = {false};
        nav.navigateTo(new Location(grid.world, 4.5, 65, 0.5), () -> arrived[0] = true, null);
        assertTrue(nav.isNavigating());

        tickUntil(() -> arrived[0], 2000);
        assertTrue(arrived[0], "arrival callback should fire");
        assertEquals(NavigationController.State.IDLE, nav.getState());
    }

    @Test
    void navigationStartedInsideArrivalCallbackIsNotClobbered() {
        boolean[] secondArrived = {false};
        Location goalA = new Location(grid.world, 3.5, 65, 0.5);
        Location goalB = new Location(grid.world, 0.5, 65, 4.5);

        nav.navigateTo(goalA,
                () -> nav.navigateTo(goalB, () -> secondArrived[0] = true, null),
                null);

        tickUntil(() -> secondArrived[0], 5000);
        assertTrue(secondArrived[0],
                "chained navigateTo started inside the arrival callback must proceed, not be reset to IDLE");
    }

    @Test
    void navigationStartedInsideFailureCallbackIsNotClobbered() {
        boolean[] recovered = {false};
        // Unreachable goal: off the walkable island.
        Location unreachable = new Location(grid.world, 60.5, 65, 60.5);
        Location reachable = new Location(grid.world, 3.5, 65, 0.5);

        nav.navigateTo(unreachable, null,
                () -> nav.navigateTo(reachable, () -> recovered[0] = true, null));

        tickUntil(() -> recovered[0], 5000);
        assertTrue(recovered[0],
                "navigateTo started inside the failure callback must proceed, not be reset to IDLE");
    }

    @Test
    void cancelWipesCallbacksAndState() {
        boolean[] arrived = {false};
        nav.navigateTo(new Location(grid.world, 4.5, 65, 0.5), () -> arrived[0] = true, null);
        nav.cancel();

        assertEquals(NavigationController.State.IDLE, nav.getState());
        assertFalse(nav.isNavigating());
        assertNull(nav.getGoalLocation());

        // Ticking after cancel must never invoke the old callback.
        for (int i = 0; i < 100; i++) nav.tick();
        assertFalse(arrived[0]);
    }
}
