package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ChatActionTest {

    private World worldA;
    private World worldB;
    private Player nearPlayer;
    private Player otherWorldPlayer;
    private AIAgent agent;

    @BeforeEach
    void setUp() {
        worldA = mock(World.class);
        worldB = mock(World.class);

        // Player in a different world is returned first so a missing world
        // guard throws before the same-world player is messaged.
        otherWorldPlayer = mock(Player.class);
        when(otherWorldPlayer.getWorld()).thenReturn(worldB);
        when(otherWorldPlayer.getLocation()).thenReturn(new Location(worldB, 1, 65, 1));

        nearPlayer = mock(Player.class);
        when(nearPlayer.getWorld()).thenReturn(worldA);
        when(nearPlayer.getLocation()).thenReturn(new Location(worldA, 2, 65, 2));

        Server server = mock(Server.class);
        doReturn(List.of(otherWorldPlayer, nearPlayer)).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn("Bob");
        when(npc.getLocation()).thenReturn(new Location(worldA, 0, 65, 0));

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void broadcastSurvivesPlayersInOtherWorlds() {
        ChatAction action = new ChatAction(agent, "hello there");

        ActionResult result = assertDoesNotThrow(action::tick,
                "cross-world distance check must not throw and kill the broadcast");

        assertEquals(ActionResult.SUCCESS, result);
        verify(nearPlayer).sendMessage(contains("hello there"));
        verify(otherWorldPlayer, never()).sendMessage(anyString());
    }

    @Test
    void distantSameWorldPlayerDoesNotReceiveMessage() {
        Player farPlayer = mock(Player.class);
        when(farPlayer.getWorld()).thenReturn(worldA);
        when(farPlayer.getLocation()).thenReturn(new Location(worldA, 500, 65, 500));
        Server server = mock(Server.class);
        doReturn(List.of(farPlayer, nearPlayer)).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        ChatAction action = new ChatAction(agent, "hi");
        assertEquals(ActionResult.SUCCESS, action.tick());
        verify(nearPlayer).sendMessage(contains("hi"));
        verify(farPlayer, never()).sendMessage(anyString());
    }
}
