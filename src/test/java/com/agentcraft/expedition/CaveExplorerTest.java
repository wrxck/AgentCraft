package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import java.util.function.UnaryOperator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaveExplorerTest {

    private TestWorld tw;
    private AIAgent agent;

    @BeforeEach
    void setUp() {
        tw = new TestWorld();
        FakePlayer npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(tw.world, 0, 40, 0));
        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
    }

    // --- Passage selection (finding 7) ---

    /** East (+x) leads to the surface; ground finder reports a high Y for it. */
    private UnaryOperator<Location> surfaceToTheEast(int cy) {
        return loc -> loc.getBlockX() > 0
                ? new Location(loc.getWorld(), loc.getX(), cy + 20, loc.getZ())
                : loc;
    }

    @Test
    void rejectedHighAirDirectionDoesNotShadowValidLowerOne() {
        int cy = 40;
        tw.setDefault(Material.STONE);
        // East (+x): fully open (20 air blocks) but leads to the surface.
        for (int d = 1; d <= 5; d++) {
            for (int dy = -1; dy <= 2; dy++) {
                tw.setType(d, cy + dy, 0, Material.CAVE_AIR);
            }
        }
        // South (+z): a 1x2 passage (10 air blocks), stays underground.
        for (int d = 1; d <= 5; d++) {
            tw.setType(0, cy, d, Material.CAVE_AIR);
            tw.setType(0, cy + 1, d, Material.CAVE_AIR);
        }

        Location passage = CaveExplorer.selectPassage(
                tw.world, 0, cy, 0, 0.0, surfaceToTheEast(cy));

        // The rejected (surface-bound) east direction must not poison the
        // comparison: the valid south passage has to be selected.
        assertNotNull(passage, "valid passage was shadowed by a rejected direction");
        assertEquals(0, passage.getBlockX());
        assertEquals(40, passage.getBlockZ());
    }

    @Test
    void picksLargestValidOpening() {
        int cy = 40;
        tw.setDefault(Material.STONE);
        // North (-z): 1x2 passage (10 air).
        for (int d = 1; d <= 5; d++) {
            tw.setType(0, cy, -d, Material.CAVE_AIR);
            tw.setType(0, cy + 1, -d, Material.CAVE_AIR);
        }
        // West (-x): full 20-air opening, underground.
        for (int d = 1; d <= 5; d++) {
            for (int dy = -1; dy <= 2; dy++) {
                tw.setType(-d, cy + dy, 0, Material.CAVE_AIR);
            }
        }

        Location passage = CaveExplorer.selectPassage(
                tw.world, 0, cy, 0, 0.0, UnaryOperator.identity());

        assertNotNull(passage);
        assertEquals(-40, passage.getBlockX());
        assertEquals(0, passage.getBlockZ());
    }

    @Test
    void returnsNullWhenExploreBudgetExhausted() {
        int cy = 40;
        tw.setDefault(Material.STONE);
        for (int d = 1; d <= 5; d++) {
            for (int dy = -1; dy <= 2; dy++) {
                tw.setType(d, cy + dy, 0, Material.CAVE_AIR);
            }
        }
        // 98 of 100 blocks used: remaining target distance < 5.
        assertNull(CaveExplorer.selectPassage(
                tw.world, 0, cy, 0, 98.0, UnaryOperator.identity()));
    }

    @Test
    void returnsNullWhenNoOpeningAtAll() {
        tw.setDefault(Material.STONE);
        assertNull(CaveExplorer.selectPassage(
                tw.world, 0, 40, 0, 0.0, UnaryOperator.identity()));
    }

    // --- Material scan (finding 4d) ---

    @Test
    void scanIgnoresPartialTokenMatches() {
        tw.setDefault(Material.AIR);
        tw.setType(1, 40, 0, Material.REDSTONE_ORE);
        tw.setType(3, 40, 0, Material.STONE);

        CaveExplorer explorer = new CaveExplorer(agent, "stone",
                new Location(tw.world, 0, 40, 0));
        Location found = explorer.scanForMaterial(new Location(tw.world, 0, 40, 0));

        assertNotNull(found);
        assertEquals(3, found.getBlockX());
    }
}
