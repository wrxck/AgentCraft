package com.agentcraft.expedition;

/**
 * Pure geometry for the 3x3 mining faces used by {@link TunnelMiner} and
 * {@link BranchMiner}. Coordinates are returned in sweep order (top row to
 * bottom row, left to right), each entry being {x, y, z}.
 */
final class MiningFaces {

    private MiningFaces() {
    }

    /**
     * TunnelMiner staircase face: the bottom row is one block below the NPC's
     * feet so the tunnel descends one step per face.
     */
    static int[][] descendingFace(int npcX, int npcY, int npcZ,
                                  int dirX, int dirZ, int perpX, int perpZ) {
        return face(npcX + dirX, npcY - 1, npcZ + dirZ, perpX, perpZ);
    }

    /**
     * BranchMiner corridor face: 3x3 at the current Y level (no descent).
     * The bottom row is at feet level, leaving the floor ahead intact.
     */
    static int[][] flatFace(int npcX, int npcY, int npcZ,
                            int dirX, int dirZ, int perpX, int perpZ) {
        return face(npcX + dirX, npcY, npcZ + dirZ, perpX, perpZ);
    }

    private static int[][] face(int bx, int by, int bz, int perpX, int perpZ) {
        int[][] coords = new int[9][];
        int idx = 0;
        for (int row = 2; row >= 0; row--) { // top to bottom
            for (int col = -1; col <= 1; col++) { // left to right
                coords[idx++] = new int[]{bx + perpX * col, by + row, bz + perpZ * col};
            }
        }
        return coords;
    }
}
