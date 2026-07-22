package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MacroNavigatorTest {

    private TestWorld tw;
    private AIAgent agent;
    private NavigationController nav;
    private MacroNavigator macro;

    @BeforeEach
    void setUp() {
        tw = new TestWorld();
        Chunk chunk = mock(Chunk.class);
        when(chunk.isLoaded()).thenReturn(true);
        when(tw.world.getChunkAt(any(Location.class))).thenReturn(chunk);

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(tw.world, 0, 64, 0));

        nav = mock(NavigationController.class);
        BehaviorController behavior = mock(BehaviorController.class);
        when(behavior.getNavigation()).thenReturn(nav);

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getBehaviorController()).thenReturn(behavior);
        when(agent.getPlugin()).thenReturn(mock(AgentCraftPlugin.class));

        macro = new MacroNavigator(agent);
    }

    @Test
    void reissuesSegmentWhenNavigationWipedWithoutCallbacks() {
        macro.navigateTo(new Location(tw.world, 100, 64, 100));
        verify(nav, times(1)).navigateTo(any(Location.class), any(), any());

        // An interrupt (e.g. combat) called nav.cancel()/navigateTo() and
        // wiped our callbacks: neither segmentArrived nor segmentFailed will
        // ever fire, and the low-level navigation is idle again.
        when(nav.isNavigating()).thenReturn(false);

        macro.tick();

        assertTrue(macro.isNavigating(), "macro navigation must stay active");
        verify(nav, times(2)).navigateTo(any(Location.class), any(), any());
    }

    @Test
    void doesNotReissueWhileSegmentInFlight() {
        macro.navigateTo(new Location(tw.world, 100, 64, 100));
        when(nav.isNavigating()).thenReturn(true);

        macro.tick();
        macro.tick();

        verify(nav, times(1)).navigateTo(any(Location.class), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void startsNextSegmentWhenArrivalCallbackFires() {
        macro.navigateTo(new Location(tw.world, 100, 64, 100));

        ArgumentCaptor<Runnable> arrival = ArgumentCaptor.forClass(Runnable.class);
        verify(nav).navigateTo(any(Location.class), arrival.capture(), any());
        arrival.getValue().run();

        macro.tick();
        verify(nav, times(2)).navigateTo(any(Location.class), any(), any());
        assertTrue(macro.isNavigating());
    }
}
