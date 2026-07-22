package com.agentcraft.npc;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NPCTrackerTest {

    private static Chunk chunk(World world, int cx, int cz) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(cx);
        when(chunk.getZ()).thenReturn(cz);
        return chunk;
    }

    @Test
    void isInChunkMatchesSameWorldAndCoords() {
        World world = mock(World.class);
        // Block (17, 64, -1) is in chunk (1, -1).
        Location loc = new Location(world, 17.5, 64, -0.5);
        assertTrue(NPCTracker.isInChunk(loc, chunk(world, 1, -1)));
        assertFalse(NPCTracker.isInChunk(loc, chunk(world, 0, -1)));
        assertFalse(NPCTracker.isInChunk(loc, chunk(world, 1, 0)));
    }

    @Test
    void isInChunkRejectsOtherWorld() {
        World worldA = mock(World.class);
        World worldB = mock(World.class);
        Location loc = new Location(worldA, 0.5, 64, 0.5);
        assertFalse(NPCTracker.isInChunk(loc, chunk(worldB, 0, 0)));
        assertTrue(NPCTracker.isInChunk(loc, chunk(worldA, 0, 0)));
    }

    @Test
    void registerAndLookupRoundTrip() {
        NPCTracker tracker = new NPCTracker();
        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getEntityId()).thenReturn(1_900_000_123);
        when(npc.getName()).thenReturn("Bob");

        tracker.register(npc);
        assertTrue(tracker.isNPC(1_900_000_123));
        assertTrue(tracker.hasName("bob"));
        assertSame(npc, tracker.getByEntityId(1_900_000_123));
        assertSame(npc, tracker.getByName("BOB"));
        assertEquals(1, tracker.count());

        tracker.unregister(npc);
        assertFalse(tracker.isNPC(1_900_000_123));
        assertEquals(0, tracker.count());
    }
}
