package com.agentcraft.navigation;

public class PathNode implements Comparable<PathNode> {

    public final int x, y, z;
    public double g; // cost from start
    public double h; // heuristic to goal
    public double f; // g + h
    public PathNode parent;

    public PathNode(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.f, other.f);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathNode other)) return false;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }

    public long packedPos() {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }
}
