package com.agentcraft.ai;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactScannerTest {

    @Test
    void sandstoneMapsToBuildingBlockChar() {
        assertEquals('#', CompactScanner.charForMaterial(Material.SANDSTONE),
                "sandstone should map to '#' (building block), not the generic solid fallback");
    }

    @Test
    void plainSandStaysGround() {
        assertEquals('.', CompactScanner.charForMaterial(Material.SAND));
        assertEquals('.', CompactScanner.charForMaterial(Material.RED_SAND));
    }

    @Test
    void existingMappingsUnchanged() {
        assertEquals('S', CompactScanner.charForMaterial(Material.STONE));
        assertEquals('W', CompactScanner.charForMaterial(Material.WATER));
        assertEquals('T', CompactScanner.charForMaterial(Material.OAK_LOG));
        assertEquals('O', CompactScanner.charForMaterial(Material.IRON_ORE));
        assertEquals(' ', CompactScanner.charForMaterial(Material.AIR));
    }
}
