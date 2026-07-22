package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActionQueueTest {

    private Plugin plugin;
    private BukkitScheduler scheduler;
    private ActionQueue queue;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        BukkitTestSupport.setServer(server);

        plugin = mock(Plugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getInt(eq("action-delay-ticks"), anyInt())).thenReturn(1);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ActionQueueTest"));

        queue = new ActionQueue(plugin);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private Runnable startAndCaptureTicker() {
        queue.start();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), captor.capture(), anyLong(), anyLong());
        return captor.getValue();
    }

    private static class ScriptedAction extends Action {
        boolean failInTick;
        boolean failInStart;
        int onFailCalls;
        int onCompleteCalls;
        int tickCalls;

        ScriptedAction(AIAgent agent) {
            super(agent);
        }

        @Override
        public void onStart() {
            if (failInStart) throw new IllegalStateException("boom in onStart");
        }

        @Override
        public ActionResult tick() {
            tickCalls++;
            if (failInTick) throw new IllegalStateException("boom in tick");
            return ActionResult.SUCCESS;
        }

        @Override
        public void onComplete() {
            onCompleteCalls++;
        }

        @Override
        public void onFail() {
            onFailCalls++;
        }
    }

    @Test
    void tickCrashInvokesOnFailBeforeDiscardingAction() {
        ScriptedAction action = new ScriptedAction(mock(AIAgent.class));
        action.failInTick = true;
        queue.add(action);

        Runnable ticker = startAndCaptureTicker();
        ticker.run(); // starts action, tick throws

        assertEquals(1, action.onFailCalls,
                "onFail must run so the action can clean up after a tick() crash");
        assertEquals(0, action.onCompleteCalls);
    }

    @Test
    void onStartCrashInvokesOnFailBeforeDiscardingAction() {
        ScriptedAction action = new ScriptedAction(mock(AIAgent.class));
        action.failInStart = true;
        queue.add(action);

        Runnable ticker = startAndCaptureTicker();
        ticker.run();

        assertEquals(1, action.onFailCalls,
                "onFail must run so the action can clean up after an onStart() crash");
        assertEquals(0, action.tickCalls);
    }

    @Test
    void successfulActionCompletesNormally() {
        ScriptedAction action = new ScriptedAction(mock(AIAgent.class));
        queue.add(action);

        Runnable ticker = startAndCaptureTicker();
        ticker.run();

        assertEquals(1, action.onCompleteCalls);
        assertEquals(0, action.onFailCalls);
    }

    @Test
    void crashedActionDoesNotBlockNextAction() {
        ScriptedAction bad = new ScriptedAction(mock(AIAgent.class));
        bad.failInTick = true;
        ScriptedAction good = new ScriptedAction(mock(AIAgent.class));
        queue.add(bad);
        queue.add(good);

        Runnable ticker = startAndCaptureTicker();
        ticker.run(); // bad crashes
        ticker.run(); // good runs

        assertEquals(1, bad.onFailCalls);
        assertEquals(1, good.onCompleteCalls);
    }
}
