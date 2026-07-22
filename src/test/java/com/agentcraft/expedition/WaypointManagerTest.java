package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WaypointManagerTest {

    private TestWorld tw;
    private WaypointManager wm;

    @BeforeEach
    void setUp() {
        tw = new TestWorld();
        wm = new WaypointManager("Bot", new Location(tw.world, 0, 64, 0));
    }

    @Test
    void accumulatesWalkedDistance() {
        wm.tick(new Location(tw.world, 1, 64, 0));
        wm.tick(new Location(tw.world, 2, 64, 0));
        wm.tick(new Location(tw.world, 2.5, 64, 0));
        assertEquals(2.5, wm.getTotalDistanceTraveled(), 1e-9);
    }

    @Test
    void teleportJumpsDoNotCountAsTravel() {
        wm.tick(new Location(tw.world, 1, 64, 0));
        wm.tick(new Location(tw.world, 2, 64, 0));
        // Teleport home (e.g. expedition cancelled): a 200-block jump in a
        // single tick is not walking and must not count as distance.
        wm.tick(new Location(tw.world, 202, 64, 0));
        assertEquals(2.0, wm.getTotalDistanceTraveled(), 1e-9);
        // Distance keeps accumulating from the new position afterwards.
        wm.tick(new Location(tw.world, 203, 64, 0));
        assertEquals(3.0, wm.getTotalDistanceTraveled(), 1e-9);
    }
}
