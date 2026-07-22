package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiningFacesTest {

    // NPC at (0, 70, 0), digging south (dirX=0, dirZ=1), perp (-1, 0).

    @Test
    void descendingFaceSpansOneBlockBelowFeetToHead() {
        int[][] face = MiningFaces.descendingFace(0, 70, 0, 0, 1, -1, 0);
        assertEquals(9, face.length);
        // Sweep order: top row to bottom row, left to right, all at z=1.
        int[][] expected = {
                {1, 71, 1}, {0, 71, 1}, {-1, 71, 1},
                {1, 70, 1}, {0, 70, 1}, {-1, 70, 1},
                {1, 69, 1}, {0, 69, 1}, {-1, 69, 1},
        };
        assertArrayEquals(expected, face);
    }

    @Test
    void flatFaceKeepsFloorIntact() {
        // "3x3 face at current Y level (no descent)": bottom row must be at
        // feet level (y=70), NOT one below (y=69), or the corridor digs up
        // the floor ahead while the move stays flat.
        int[][] face = MiningFaces.flatFace(0, 70, 0, 0, 1, -1, 0);
        assertEquals(9, face.length);
        int[][] expected = {
                {1, 72, 1}, {0, 72, 1}, {-1, 72, 1},
                {1, 71, 1}, {0, 71, 1}, {-1, 71, 1},
                {1, 70, 1}, {0, 70, 1}, {-1, 70, 1},
        };
        assertArrayEquals(expected, face);
    }

    @Test
    void faceAppliesDirectionOffset() {
        // Digging east (dirX=1, dirZ=0), perp (0, 1): face is at x+1.
        int[][] face = MiningFaces.descendingFace(10, 40, -5, 1, 0, 0, 1);
        for (int[] coord : face) {
            assertEquals(11, coord[0]);
        }
        // Columns spread along z: -6, -5, -4 appear three times each.
        assertEquals(-6, face[0][2]);
        assertEquals(-5, face[1][2]);
        assertEquals(-4, face[2][2]);
    }
}
