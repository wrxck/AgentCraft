package com.agentcraft.behavior;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChunkForceManagerTest {

    private World worldA;
    private World worldB;
    private ChunkForceManager manager;

    @BeforeEach
    void setUp() {
        worldA = mock(World.class);
        when(worldA.getUID()).thenReturn(UUID.randomUUID());
        worldB = mock(World.class);
        when(worldB.getUID()).thenReturn(UUID.randomUUID());

        Server server = mock(Server.class);
        when(server.getWorlds()).thenReturn(List.of(worldA, worldB));
        BukkitTestSupport.setServer(server);

        // radius 0 => exactly one chunk per agent
        manager = new ChunkForceManager(Logger.getLogger("ChunkForceManagerTest"), true, 0);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private AIAgent agentAt(String name, World world, int blockX, int blockZ) {
        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getName()).thenReturn(name);
        when(npc.getLocation()).thenReturn(new Location(world, blockX, 65, blockZ));
        AIAgent agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        return agent;
    }

    @Test
    void sameChunkCoordsInDifferentWorldsAreIndependent() {
        AIAgent inA = agentAt("alice", worldA, 0, 0);
        AIAgent inB = agentAt("bob", worldB, 0, 0);

        manager.updateForced(inA);
        manager.updateForced(inB);

        // Both worlds must get their own force-load, not share one refcount.
        verify(worldA).setChunkForceLoaded(0, 0, true);
        verify(worldB).setChunkForceLoaded(0, 0, true);

        manager.releaseAll("alice");
        verify(worldA).setChunkForceLoaded(0, 0, false);
        verify(worldB, never()).setChunkForceLoaded(anyInt(), anyInt(), eq(false));
    }

    @Test
    void releaseAllOfOneHolderLeavesOtherHoldersChunkForced() {
        AIAgent alice = agentAt("alice", worldA, 0, 0);
        AIAgent bob = agentAt("bob", worldA, 0, 0);

        manager.updateForced(alice);
        manager.updateForced(bob);
        verify(worldA, times(1)).setChunkForceLoaded(0, 0, true);

        manager.releaseAll("alice");
        // Bob still holds the chunk: it must not be unforced, in any world.
        verify(worldA, never()).setChunkForceLoaded(anyInt(), anyInt(), eq(false));
        verify(worldB, never()).setChunkForceLoaded(anyInt(), anyInt(), eq(false));

        manager.releaseAll("bob");
        verify(worldA).setChunkForceLoaded(0, 0, false);
        verify(worldB, never()).setChunkForceLoaded(anyInt(), anyInt(), eq(false));
    }

    @Test
    void releaseAllReleasesEachHeldTicketExactlyOnce() {
        AIAgent alice = agentAt("alice", worldA, 0, 0);
        manager.updateForced(alice);
        manager.releaseAll("alice");
        // One held ticket, one release.
        verify(worldA, times(1)).setChunkForceLoaded(0, 0, false);
        verify(worldB, never()).setChunkForceLoaded(anyInt(), anyInt(), eq(false));
        // Releasing again is a no-op.
        manager.releaseAll("alice");
        verify(worldA, times(1)).setChunkForceLoaded(0, 0, false);
    }

    @Test
    void movingUpdatesForcedChunks() {
        AIAgent alice = agentAt("alice", worldA, 0, 0);
        manager.updateForced(alice);
        verify(worldA).setChunkForceLoaded(0, 0, true);

        // Move far away: chunk (10, 10)
        when(alice.getNpc().getLocation()).thenReturn(new Location(worldA, 160, 65, 160));
        manager.updateForced(alice);
        verify(worldA).setChunkForceLoaded(0, 0, false);
        verify(worldA).setChunkForceLoaded(10, 10, true);
    }
}
