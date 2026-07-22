package com.agentcraft.listener;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.AIIntegration;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatListenerTest {

    private MockedStatic<AgentManager> agentManagerStatic;
    private AIIntegration aiIntegration;
    private AIAgent agent;
    private World world;
    private Player player;
    private ChatListener listener;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        });
        BukkitTestSupport.setServer(server);

        world = mock(World.class);

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn("Bob");
        when(npc.getLocation()).thenReturn(new Location(world, 0.5, 65, 0.5));

        AgentCraftPlugin plugin = mock(AgentCraftPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getConfig()).thenReturn(config);
        aiIntegration = mock(AIIntegration.class);
        when(plugin.getAiIntegration()).thenReturn(aiIntegration);

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        doReturn(plugin).when(agent).getPlugin();

        AgentManager manager = mock(AgentManager.class);
        when(manager.getAllAgents()).thenReturn(List.of(agent));
        agentManagerStatic = mockStatic(AgentManager.class);
        agentManagerStatic.when(AgentManager::getInstance).thenReturn(manager);

        player = mock(Player.class);
        when(player.hasPermission("agentcraft.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 2.5, 65, 2.5));

        listener = new ChatListener(plugin);
    }

    @AfterEach
    void tearDown() {
        agentManagerStatic.close();
        BukkitTestSupport.clearServer();
    }

    private void chat(String message) {
        listener.onChat(new AsyncPlayerChatEvent(true, player, message, new HashSet<>()));
    }

    @Test
    void mentionWithTaskSubmitsTask() {
        chat("Bob, come here");
        verify(aiIntegration).submitTask(agent, player, "come here");
    }

    @Test
    void mentionInsideAnotherWordIsNotATask() {
        // "bobby" must not be treated as a mention of agent "Bob".
        chat("bobby is my friend");
        verify(aiIntegration, never()).submitTask(any(), any(), anyString());
    }

    @Test
    void nameOnlyMessageIsNotATask() {
        chat("Bob");
        verify(aiIntegration, never()).submitTask(any(), any(), anyString());
    }

    @Test
    void mentionIsCaseInsensitiveAndStripsColon() {
        chat("BOB: mine some iron");
        verify(aiIntegration).submitTask(agent, player, "mine some iron");
    }

    @Test
    void outOfRangeMentionIsIgnored() {
        when(player.getLocation()).thenReturn(new Location(world, 100.5, 65, 100.5));
        chat("Bob, come here");
        verify(aiIntegration, never()).submitTask(any(), any(), anyString());
    }

    @Test
    void otherWorldMentionIsIgnored() {
        World other = mock(World.class);
        when(player.getWorld()).thenReturn(other);
        when(player.getLocation()).thenReturn(new Location(other, 2.5, 65, 2.5));
        chat("Bob, come here");
        verify(aiIntegration, never()).submitTask(any(), any(), anyString());
    }

    @Test
    void withoutPermissionNothingHappens() {
        when(player.hasPermission("agentcraft.use")).thenReturn(false);
        chat("Bob, come here");
        verify(aiIntegration, never()).submitTask(any(), any(), anyString());
    }
}
