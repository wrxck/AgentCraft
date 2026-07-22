package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttackActionTest {

    private World worldA;
    private World worldB;
    private AIAgent agent;
    private NavigationController nav;
    private LivingEntity target;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        worldA = mock(World.class);
        worldB = mock(World.class);

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(worldA, 0, 65, 0));

        nav = mock(NavigationController.class);
        BehaviorController behavior = mock(BehaviorController.class);
        when(behavior.getNavigation()).thenReturn(nav);

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getBehaviorController()).thenReturn(behavior);

        target = mock(LivingEntity.class);
        when(target.isDead()).thenReturn(false);
        when(target.getLocation()).thenReturn(new Location(worldA, 20, 65, 0));
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void chaseIsRetriedWhenNavigationDies() {
        // Navigation never becomes active (e.g. pathing failed immediately).
        when(nav.isNavigating()).thenReturn(false);

        AttackAction action = new AttackAction(agent, target);
        action.onStart();
        for (int i = 0; i < 5; i++) {
            assertEquals(ActionResult.CONTINUE, action.tick());
        }

        // Without retry the action freezes: navigateTo would be called exactly
        // once (from onStart) and never again even though the NPC is stuck.
        verify(nav, atLeast(2)).navigateTo(any(Location.class));
    }

    @Test
    void timeoutWithoutLandingAHitReportsFailure() {
        when(nav.isNavigating()).thenReturn(false);

        AttackAction action = new AttackAction(agent, target);
        action.onStart();

        ActionResult result = ActionResult.CONTINUE;
        for (int i = 0; i < 201 && result == ActionResult.CONTINUE; i++) {
            result = action.tick();
        }
        assertEquals(ActionResult.FAILED, result,
                "timing out without ever reaching the target must not be reported as SUCCESS");
        verify(target, never()).damage(anyDouble());
    }

    @Test
    void targetInAnotherWorldFailsInsteadOfThrowing() {
        when(target.getLocation()).thenReturn(new Location(worldB, 20, 65, 0));

        AttackAction action = new AttackAction(agent, target);
        action.onStart();
        ActionResult result = assertDoesNotThrow(action::tick,
                "cross-world distance must not throw out of tick()");
        assertEquals(ActionResult.FAILED, result);
    }

    @Test
    void deadTargetIsSuccess() {
        when(target.isDead()).thenReturn(true);
        AttackAction action = new AttackAction(agent, target);
        action.onStart();
        assertEquals(ActionResult.SUCCESS, action.tick());
    }
}
