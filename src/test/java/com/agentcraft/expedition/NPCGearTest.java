package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NPCGearTest {

    @Test
    void takeDamageAppliesArmorReduction() {
        NPCGear gear = new NPCGear();
        gear.takeDamage(10.0); // 80% reduction -> 2.0 effective
        assertEquals(18.0, gear.getHp(), 1e-9);
        assertFalse(gear.isDead());
    }

    @Test
    void hpIsFlooredAtZeroAndMarksDeath() {
        NPCGear gear = new NPCGear();
        for (int i = 0; i < 30; i++) {
            gear.takeDamage(10.0);
        }
        assertEquals(0.0, gear.getHp(), 1e-9);
        assertTrue(gear.isDead());
    }

    @Test
    void tryEatHealsConsumesFoodAndCapsAtMax() {
        NPCGear gear = new NPCGear();
        gear.takeDamage(20.0); // hp 16
        assertTrue(gear.tryEat());
        assertEquals(20.0, gear.getHp(), 1e-9, "heal must cap at max HP");
        assertEquals(15, gear.getFoodCount());
    }

    @Test
    void tryEatFailsWithoutFood() {
        NPCGear gear = new NPCGear();
        for (int i = 0; i < 16; i++) {
            assertTrue(gear.tryEat());
        }
        assertEquals(0, gear.getFoodCount());
        assertFalse(gear.tryEat());
    }

    @Test
    void shouldEatOnlyBelowThresholdWithFoodAvailable() {
        NPCGear gear = new NPCGear();
        assertFalse(gear.shouldEat(), "full HP must not trigger eating");
        gear.takeDamage(20.0); // hp 16
        assertFalse(gear.shouldEat());
        gear.takeDamage(40.0); // hp 8 < 10
        assertTrue(gear.shouldEat());

        // Exhaust food: shouldEat must go false even at low HP.
        for (int i = 0; i < 16; i++) {
            gear.tryEat();
            gear.takeDamage(100.0);
        }
        assertFalse(gear.shouldEat());
    }

    @Test
    void displayAndStaticStats() {
        NPCGear gear = new NPCGear();
        assertEquals(20.0, gear.getMaxHp(), 1e-9);
        assertEquals(12.0, gear.getSwordDamage(), 1e-9);
        assertEquals("20/20", gear.getHpDisplay());
    }
}
