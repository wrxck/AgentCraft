package com.agentcraft.util;

import org.bukkit.Material;

/**
 * Shared material-name matching used by gathering, expedition scanning, and
 * crafting. Matches on whole underscore-delimited tokens so that a target of
 * "STONE" matches STONE and STONE_BRICKS but not REDSTONE_ORE, COBBLESTONE,
 * or GLOWSTONE (which a plain substring test would).
 */
public final class MaterialMatcher {

    private MaterialMatcher() {
    }

    /** Uppercases, trims, and converts spaces to underscores. */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toUpperCase().replace(' ', '_');
    }

    /**
     * True if the normalized target appears in the material name as a
     * consecutive sequence of whole underscore-delimited tokens.
     * An exact name match always succeeds.
     */
    public static boolean matches(Material material, String target) {
        return matchesName(material.name(), target);
    }

    static boolean matchesName(String materialName, String target) {
        String normalized = normalize(target);
        if (normalized.isEmpty()) {
            return false;
        }
        if (materialName.equals(normalized)) {
            return true;
        }
        String[] nameTokens = materialName.split("_");
        String[] targetTokens = normalized.split("_");
        if (targetTokens.length > nameTokens.length) {
            return false;
        }
        for (int start = 0; start <= nameTokens.length - targetTokens.length; start++) {
            boolean allMatch = true;
            for (int i = 0; i < targetTokens.length; i++) {
                if (!nameTokens[start + i].equals(targetTokens[i])) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }
}
