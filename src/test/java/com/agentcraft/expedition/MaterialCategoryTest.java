package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialCategoryTest {

    @Test
    void resolvesSpacedNamesLikeUnderscoredOnes() {
        // Root cause of expeditions for "ancient debris" strolling around
        // the surface: resolve() never converted spaces to underscores.
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("ancient debris"));
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("ancient_debris"));
        assertEquals(MaterialCategory.UNDERGROUND_STRUCTURE, MaterialCategory.resolve("stone brick"));
        assertEquals(MaterialCategory.UNDERGROUND_STRUCTURE, MaterialCategory.resolve("stone_bricks"));
        assertEquals(MaterialCategory.UNDERGROUND_ORE, MaterialCategory.resolve("iron ore"));
    }

    @Test
    void resolvesDeepOres() {
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("diamond"));
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("DIAMOND_ORE"));
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("redstone"));
        assertEquals(MaterialCategory.UNDERGROUND_DEEP, MaterialCategory.resolve("deepslate"));
    }

    @Test
    void resolvesRegularOres() {
        assertEquals(MaterialCategory.UNDERGROUND_ORE, MaterialCategory.resolve("iron_ore"));
        assertEquals(MaterialCategory.UNDERGROUND_ORE, MaterialCategory.resolve("coal"));
        assertEquals(MaterialCategory.UNDERGROUND_ORE, MaterialCategory.resolve("copper"));
    }

    @Test
    void resolvesSurfaceAndCommonMaterials() {
        assertEquals(MaterialCategory.SURFACE, MaterialCategory.resolve("oak_log"));
        assertEquals(MaterialCategory.SURFACE, MaterialCategory.resolve("sand"));
        assertEquals(MaterialCategory.UNDERGROUND_COMMON, MaterialCategory.resolve("stone"));
        assertEquals(MaterialCategory.UNDERGROUND_COMMON, MaterialCategory.resolve("cobblestone"));
    }

    @Test
    void unknownMaterialDefaultsToSurface() {
        assertEquals(MaterialCategory.SURFACE, MaterialCategory.resolve("nether_star"));
        assertEquals(MaterialCategory.SURFACE, MaterialCategory.resolve(""));
    }

    @Test
    void targetYIsWithinCategoryBand() {
        for (MaterialCategory category : MaterialCategory.values()) {
            int y = category.getTargetY();
            assertTrue(y >= category.getMinY() && y <= category.getMaxY(),
                    category + " targetY " + y + " outside band");
        }
        assertEquals(-24, MaterialCategory.UNDERGROUND_DEEP.getTargetY());
        assertTrue(MaterialCategory.SURFACE.isSurface());
        assertFalse(MaterialCategory.UNDERGROUND_DEEP.isSurface());
        assertTrue(MaterialCategory.UNDERGROUND_DEEP.isUnderground());
    }
}
