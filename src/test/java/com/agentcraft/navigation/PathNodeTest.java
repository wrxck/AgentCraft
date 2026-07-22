package com.agentcraft.navigation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathNodeTest {

    @Test
    void equalsAndHashCodeAreConsistent() {
        PathNode a = new PathNode(10, -60, -12345);
        PathNode b = new PathNode(10, -60, -12345);
        PathNode c = new PathNode(10, -60, -12344);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertEquals(a, a);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a node");

        // f/g/h must not affect equality
        a.f = 12.0;
        b.f = 99.0;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void packedPosRoundTripsNegativeCoordsAndFullYRange() {
        int[] xs = {-30_000_000, -12345, -1, 0, 1, 12345, 30_000_000};
        int[] ys = {-64, -1, 0, 64, 319, 320};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : xs) {
                    PathNode node = new PathNode(x, y, z);
                    long packed = node.packedPos();
                    assertEquals(x, unpackX(packed), "x for " + x + "," + y + "," + z);
                    assertEquals(y, unpackY(packed), "y for " + x + "," + y + "," + z);
                    assertEquals(z, unpackZ(packed), "z for " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void packedPosIsInjectiveAcrossDistinctPositions() {
        Set<Long> seen = new HashSet<>();
        int[] coords = {-17, -1, 0, 1, 17};
        int[] ys = {-64, 0, 320};
        for (int x : coords) {
            for (int y : ys) {
                for (int z : coords) {
                    assertTrue(seen.add(new PathNode(x, y, z).packedPos()),
                            "collision at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void compareToOrdersByF() {
        PathNode low = new PathNode(0, 0, 0);
        low.f = 1.0;
        PathNode high = new PathNode(1, 0, 0);
        high.f = 2.0;
        PathNode alsoLow = new PathNode(2, 0, 0);
        alsoLow.f = 1.0;

        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(alsoLow));
    }

    // Layout: x in bits 38-63 (26 bits), y in bits 26-37 (12 bits), z in bits 0-25 (26 bits).
    private static int unpackX(long packed) {
        return (int) (packed << (64 - 26 - 38) >> (64 - 26));
    }

    private static int unpackY(long packed) {
        return (int) (packed << (64 - 12 - 26) >> (64 - 12));
    }

    private static int unpackZ(long packed) {
        return (int) (packed << (64 - 26) >> (64 - 26));
    }
}
