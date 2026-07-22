package com.agentcraft.listener;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.ConversationManager;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatForwarderTest {

    private MockedStatic<AgentManager> agentManagerStatic;
    private ConversationManager conversationManager;
    private World world;
    private Player player;
    private ChatForwarder forwarder;

    @BeforeEach
    void setUp() {
        BukkitTestSupport.setServer(mock(Server.class));

        world = mock(World.class);

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn("Bob");
        when(npc.getLocation()).thenReturn(new Location(world, 0.5, 65, 0.5));

        AgentCraftPlugin plugin = mock(AgentCraftPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getConfig()).thenReturn(config);

        AIAgent agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        doReturn(plugin).when(agent).getPlugin();

        AgentManager manager = mock(AgentManager.class);
        when(manager.getAllAgents()).thenReturn(List.of(agent));
        agentManagerStatic = mockStatic(AgentManager.class);
        agentManagerStatic.when(AgentManager::getInstance).thenReturn(manager);

        player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");
        when(player.hasPermission("agentcraft.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 2.5, 65, 2.5));

        conversationManager = mock(ConversationManager.class);
        forwarder = new ChatForwarder(List.of("Steve"), conversationManager);
    }

    @AfterEach
    void tearDown() {
        agentManagerStatic.close();
        BukkitTestSupport.clearServer();
    }

    private void chat(String message) {
        forwarder.onChat(new AsyncPlayerChatEvent(true, player, message, new HashSet<>()));
    }

    @Test
    void actionableCodeTaskIsNotForwarded() {
        // In range, has permission, non-empty task: ChatListener will handle it.
        chat("Bob, come here");
        verify(conversationManager, never()).onChatMessage(anyString(), anyString());
    }

    @Test
    void outOfRangeMentionIsForwardedToConversation() {
        // ChatListener will NOT act (out of range), so the message must not be
        // swallowed: it goes to the conversation path instead.
        when(player.getLocation()).thenReturn(new Location(world, 100.5, 65, 100.5));
        chat("Bob, come here");
        verify(conversationManager).onChatMessage("Steve", "Bob, come here");
    }

    @Test
    void otherWorldMentionIsForwardedToConversation() {
        World other = mock(World.class);
        when(player.getWorld()).thenReturn(other);
        when(player.getLocation()).thenReturn(new Location(other, 2.5, 65, 2.5));
        chat("Bob, come here");
        verify(conversationManager).onChatMessage("Steve", "Bob, come here");
    }

    @Test
    void mentionInsideAnotherWordIsForwarded() {
        // "bobby" is not a mention of "Bob"; the message must be forwarded.
        chat("bobby is my friend");
        verify(conversationManager).onChatMessage("Steve", "bobby is my friend");
    }

    @Test
    void plainMessageIsForwarded() {
        chat("nice weather today");
        verify(conversationManager).onChatMessage("Steve", "nice weather today");
    }

    @Test
    void nameOnlyMessageIsForwarded() {
        chat("Bob");
        verify(conversationManager).onChatMessage("Steve", "Bob");
    }

    @Test
    void nonWhitelistedPlayerIsNotForwarded() {
        when(player.getName()).thenReturn("Alex");
        chat("hello");
        verify(conversationManager, never()).onChatMessage(anyString(), anyString());
    }
}
