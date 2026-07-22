package com.agentcraft.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.npc.NPCTracker;
import com.agentcraft.testutil.BukkitTestSupport;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerJoinListenerTest {

    private NPCTracker tracker;
    private PlayerJoinListener listener;
    private Player player;
    private final AtomicReference<Runnable> scheduled = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        tracker = mock(NPCTracker.class);
        Plugin plugin = mock(Plugin.class);
        listener = new PlayerJoinListener(tracker, plugin);
        player = mock(Player.class);

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
                .thenAnswer(inv -> {
                    scheduled.set(inv.getArgument(1));
                    return null;
                });
        BukkitTestSupport.setServer(server);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void delayedShowIsSkippedWhenPlayerAlreadyDisconnected() {
        when(player.isOnline()).thenReturn(false);

        listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
        scheduled.get().run();

        verify(tracker, never()).showToPlayer(any(Player.class));
    }

    @Test
    void delayedShowRunsForOnlinePlayer() {
        when(player.isOnline()).thenReturn(true);

        listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
        scheduled.get().run();

        verify(tracker).showToPlayer(player);
    }
}
