package com.agentcraft.expedition;

import com.agentcraft.util.MaterialMatcher;

public enum MaterialCategory {

    SURFACE(64, 320),
    UNDERGROUND_COMMON(0, 64),
    UNDERGROUND_ORE(0, 48),
    UNDERGROUND_DEEP(-64, 16),
    UNDERGROUND_STRUCTURE(0, 40);

    private final int minY;
    private final int maxY;

    MaterialCategory(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }

    public int getTargetY() {
        return (minY + maxY) / 2;
    }

    public boolean isSurface() {
        return this == SURFACE;
    }

    public boolean isUnderground() {
        return !isSurface();
    }

    public static MaterialCategory resolve(String materialName) {
        // Normalize like MaterialMatcher (trim, spaces -> underscores), then
        // lowercase for the contains() checks: "ancient debris" must resolve
        // exactly like "ancient_debris".
        String name = MaterialMatcher.normalize(materialName).toLowerCase();

        // Surface materials
        if (name.contains("log") || name.contains("leaves") || name.contains("sapling")
                || name.contains("sand") || name.contains("flower") || name.contains("dandelion")
                || name.contains("poppy") || name.contains("tulip") || name.contains("orchid")
                || name.contains("allium") || name.contains("bluet") || name.contains("daisy")
                || name.contains("cornflower") || name.contains("lily") || name.contains("grass")
                || name.contains("fern") || name.contains("mushroom") || name.contains("cactus")
                || name.contains("sugar_cane") || name.contains("bamboo") || name.contains("vine")
                || name.contains("pumpkin") || name.contains("melon") || name.contains("hay")
                || name.contains("wool") || name.contains("dirt") || name.contains("clay")
                || name.contains("gravel")) {
            return SURFACE;
        }

        // Structure materials (stronghold, dungeon, mineshaft)
        if (name.contains("stone_brick") || name.contains("mossy") || name.contains("cracked")
                || name.contains("spawner") || name.contains("bookshelf") || name.contains("end_portal")
                || name.contains("rail") || name.contains("fence") || name.contains("chest")
                || name.contains("cobweb")) {
            return UNDERGROUND_STRUCTURE;
        }

        // Deep ores
        if (name.contains("diamond") || name.contains("lapis") || name.contains("redstone")
                || name.contains("emerald") || name.contains("ancient_debris")
                || name.contains("deepslate")) {
            return UNDERGROUND_DEEP;
        }

        // Regular ores
        if (name.contains("coal") || name.contains("iron") || name.contains("gold")
                || name.contains("copper") || name.contains("ore")) {
            return UNDERGROUND_ORE;
        }

        // Common underground
        if (name.contains("stone") || name.contains("cobblestone") || name.contains("andesite")
                || name.contains("diorite") || name.contains("granite") || name.contains("tuff")
                || name.contains("obsidian") || name.contains("glowstone")) {
            return UNDERGROUND_COMMON;
        }

        // Default to surface
        return SURFACE;
    }
}
