package com.agentcraft.ai;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamProcessorTest {

    private Server server;
    private World npcWorld;
    private World otherWorld;
    private AIAgent agent;
    private FakePlayer npc;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        BukkitTestSupport.setServer(server);

        npcWorld = mock(World.class);
        otherWorld = mock(World.class);

        npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(npcWorld, 0, 64, 0));
        when(npc.getName()).thenReturn("Bot");

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    @Test
    void broadcastSkipsPlayersInOtherWorlds() {
        Player sameWorldPlayer = mock(Player.class);
        when(sameWorldPlayer.getLocation()).thenReturn(new Location(npcWorld, 5, 64, 5));

        Player otherWorldPlayer = mock(Player.class);
        when(otherWorldPlayer.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));

        // Other-world player first so a cross-world distance call fails loudly.
        doReturn(List.of(otherWorldPlayer, sameWorldPlayer)).when(server).getOnlinePlayers();

        StreamProcessor processor = new StreamProcessor(agent);
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"hello\"}]}}");

        assertDoesNotThrow(() -> processor.process(event),
                "a player in a different world must not kill the broadcast loop");
        verify(sameWorldPlayer).sendMessage(anyString());
        verify(otherWorldPlayer, never()).sendMessage(anyString());
    }

    @Test
    void broadcastSkipsPlayersOutOfRange() {
        Player farPlayer = mock(Player.class);
        when(farPlayer.getLocation()).thenReturn(new Location(npcWorld, 500, 64, 500));

        doReturn(List.of(farPlayer)).when(server).getOnlinePlayers();

        StreamProcessor processor = new StreamProcessor(agent);
        StreamEvent event = StreamEvent.parse(
                "{\"type\":\"assistant\",\"message\":{\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"hello\"}]}}");

        processor.process(event);
        verify(farPlayer, never()).sendMessage(anyString());
    }
}
