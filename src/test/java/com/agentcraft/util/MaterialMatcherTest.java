package com.agentcraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MaterialMatcherTest {

    @Test
    void normalizeUppercasesTrimsAndReplacesSpaces() {
        assertEquals("IRON_ORE", MaterialMatcher.normalize("iron ore"));
        assertEquals("ANCIENT_DEBRIS", MaterialMatcher.normalize("  ancient debris "));
        assertEquals("OAK_LOG", MaterialMatcher.normalize("OAK_LOG"));
        assertEquals("", MaterialMatcher.normalize(null));
        assertEquals("", MaterialMatcher.normalize("   "));
    }

    @Test
    void exactNameAlwaysMatches() {
        assertTrue(MaterialMatcher.matches(Material.STONE, "stone"));
        assertTrue(MaterialMatcher.matches(Material.IRON_ORE, "iron ore"));
    }

    @Test
    void matchesWholeTokenPrefixOrSuffix() {
        assertTrue(MaterialMatcher.matches(Material.STONE_BRICKS, "stone"));
        assertTrue(MaterialMatcher.matches(Material.DEEPSLATE_IRON_ORE, "iron_ore"));
        assertTrue(MaterialMatcher.matches(Material.OAK_LOG, "log"));
        assertTrue(MaterialMatcher.matches(Material.DARK_OAK_LOG, "oak_log"));
        assertTrue(MaterialMatcher.matches(Material.IRON_ORE, "iron"));
    }

    @Test
    void rejectsPartialTokenMatches() {
        // A plain substring test would match all of these for "stone".
        assertFalse(MaterialMatcher.matches(Material.REDSTONE_ORE, "stone"));
        assertFalse(MaterialMatcher.matches(Material.COBBLESTONE, "stone"));
        assertFalse(MaterialMatcher.matches(Material.GLOWSTONE, "stone"));
        assertFalse(MaterialMatcher.matches(Material.STICK, "stick_house"));
        // Whole-token matches are intentionally allowed even when the
        // target is only part of the name.
        assertTrue(MaterialMatcher.matches(Material.WHEAT_SEEDS, "wheat"));
    }

    @Test
    void tokensMustBeConsecutive() {
        assertFalse(MaterialMatcher.matchesName("IRON_BLOCK_ORE", "IRON_ORE"));
        assertTrue(MaterialMatcher.matchesName("DEEPSLATE_GOLD_ORE", "GOLD_ORE"));
    }

    @Test
    void emptyOrNullTargetNeverMatches() {
        assertFalse(MaterialMatcher.matches(Material.STONE, null));
        assertFalse(MaterialMatcher.matches(Material.STONE, ""));
        assertFalse(MaterialMatcher.matches(Material.STONE, "  "));
    }
}
