package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpeditionCombatHandlerTest {

    private TestWorld tw;
    private NavigationController nav;
    private ExpeditionCombatHandler handler;
    private LivingEntity zombie;
    private AtomicReference<Location> zombieLoc;
    private AtomicBoolean zombieDead;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        tw = new TestWorld();

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(tw.world, 0, 64, 0));

        nav = mock(NavigationController.class);
        BehaviorController behavior = mock(BehaviorController.class);
        when(behavior.getNavigation()).thenReturn(nav);

        AIAgent agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getBehaviorController()).thenReturn(behavior);

        zombieDead = new AtomicBoolean(false);
        zombieLoc = new AtomicReference<>(new Location(tw.world, 1, 64, 0));
        zombie = mock(LivingEntity.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(zombie.getLocation()).thenAnswer(inv -> zombieLoc.get().clone());
        when(zombie.isDead()).thenAnswer(inv -> zombieDead.get());
        doAnswer(inv -> {
            zombieDead.set(true);
            return null;
        }).when(zombie).damage(anyDouble());

        doReturn(List.of(zombie)).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());

        handler = new ExpeditionCombatHandler(agent, new NPCGear());
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void restartsChaseWhenTargetLeavesReachAfterNavCancelled() {
        // Zombie never dies in this scenario.
        doAnswer(inv -> null).when(zombie).damage(anyDouble());

        assertEquals(ExpeditionCombatHandler.CombatResult.FIGHT, handler.scan());
        verify(nav, times(1)).navigateTo(any(Location.class));

        // Close the distance: handler attacks and cancels navigation.
        handler.tick();
        verify(nav).cancel();

        // Target retreats out of reach; navigation is no longer active.
        zombieLoc.set(new Location(tw.world, 10, 64, 0));
        when(nav.isNavigating()).thenReturn(false);

        handler.tick();

        // The handler must re-issue the chase, not freeze.
        verify(nav, times(2)).navigateTo(any(Location.class));
        assertTrue(handler.isFighting(), "handler should still be fighting");
    }

    @Test
    void killEndsFight() {
        assertEquals(ExpeditionCombatHandler.CombatResult.FIGHT, handler.scan());
        handler.tick(); // in reach -> attack -> zombie dies

        assertTrue(handler.isDone());
        assertEquals(1, handler.getKillCount());
    }

    @Test
    void capturesKilledMobNameForReportingExactlyOnce() {
        assertEquals(ExpeditionCombatHandler.CombatResult.FIGHT, handler.scan());
        handler.tick(); // kill

        assertEquals("zombie", handler.consumeLastKillName());
        assertNull(handler.consumeLastKillName(), "kill name must clear on read");
    }

    @Test
    void fleeingRecordsActualThreatCount() {
        LivingEntity[] pack = new LivingEntity[4];
        pack[0] = zombie;
        for (int i = 1; i < 4; i++) {
            LivingEntity mob = mock(LivingEntity.class);
            when(mob.getType()).thenReturn(EntityType.ZOMBIE);
            when(mob.getLocation()).thenReturn(new Location(tw.world, 2 + i, 64, 0));
            pack[i] = mob;
        }
        doReturn(List.of(pack)).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());

        assertEquals(ExpeditionCombatHandler.CombatResult.FLEE, handler.scan());
        assertEquals(4, handler.getLastThreatCount());
    }
}
