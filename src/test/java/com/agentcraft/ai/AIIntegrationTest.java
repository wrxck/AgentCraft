package com.agentcraft.ai;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentProfile;
import com.agentcraft.agent.AgentState;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIIntegrationTest {

    @TempDir
    File workDir;

    private AgentCraftPlugin plugin;
    private FileConfiguration config;
    private AIAgent agent;
    private Player requester;
    private final List<Runnable> scheduledTasks = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(inv -> {
            scheduledTasks.add(inv.getArgument(1));
            return mock(BukkitTask.class);
        });
        BukkitTestSupport.setServer(server);

        plugin = mock(AgentCraftPlugin.class);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AIIntegrationTest"));
        when(config.getString(eq("claude-cli-path"), anyString())).thenReturn("/bin/false");

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn("Bot");

        AgentProfile profile = mock(AgentProfile.class);
        when(profile.getName()).thenReturn("Bot");
        when(profile.getPersonality()).thenReturn("friendly");
        when(profile.getSpecialty()).thenReturn("building");
        when(profile.getMaxTurns()).thenReturn(1);

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getProfile()).thenReturn(profile);
        when(agent.getWorkingDirectory()).thenReturn(workDir);
        when(agent.getBehaviorController()).thenReturn(mock(BehaviorController.class));

        requester = mock(Player.class);
        when(requester.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    @Timeout(15)
    void busyRejectionDoesNotConsumeRateLimitToken() {
        when(config.getInt(eq("rate-limit-tokens"), anyInt())).thenReturn(1);
        when(config.getInt(eq("rate-limit-refill-seconds"), anyInt())).thenReturn(3600);
        AIIntegration integration = new AIIntegration(plugin);

        // Agent busy: the request is rejected but must not cost the single token.
        when(agent.getState()).thenReturn(AgentState.THINKING);
        integration.submitTask(agent, requester, "build a hut");
        verify(agent, never()).assignTask(anyString());

        // Agent idle: the token must still be available.
        when(agent.getState()).thenReturn(AgentState.IDLE);
        integration.submitTask(agent, requester, "build a hut");
        verify(agent).assignTask("build a hut");
    }

    @Test
    @Timeout(15)
    void staleExitCleanupDoesNotRemoveNewerClient() throws Exception {
        when(config.getInt(eq("rate-limit-tokens"), anyInt())).thenReturn(10);
        when(config.getInt(eq("rate-limit-refill-seconds"), anyInt())).thenReturn(60);
        when(agent.getState()).thenReturn(AgentState.IDLE);
        AIIntegration integration = new AIIntegration(plugin);

        // First task: client A registers, its process fails fast and schedules cleanup.
        integration.submitTask(agent, requester, "task one");
        ClaudeClient clientA = integration.clients.get("bot");
        assertNotNull(clientA);

        long deadline = System.currentTimeMillis() + 10_000;
        while (scheduledTasks.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(!scheduledTasks.isEmpty(), "client A's exit cleanup should be scheduled");
        Runnable staleCleanup = scheduledTasks.get(0);

        // Second task arrives before A's cleanup runs: client B replaces A.
        integration.submitTask(agent, requester, "task two");
        ClaudeClient clientB = integration.clients.get("bot");
        assertNotNull(clientB);
        assertNotSame(clientA, clientB);

        // Now A's stale cleanup fires. It must not unregister the new client B.
        staleCleanup.run();
        assertSame(clientB, integration.clients.get("bot"),
                "stale exit cleanup must not remove the newer registered client");
    }
}
