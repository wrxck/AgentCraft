package com.agentcraft.expedition;

public class NPCGear {

    private static final double MAX_HP = 20.0;
    private static final double ARMOR_REDUCTION = 0.60;
    private static final double SWORD_DAMAGE = 7.0;
    private static final int MAX_PICKAXE_DURABILITY = 250;
    private static final double FOOD_HEAL = 6.0;
    private static final double EAT_THRESHOLD = 10.0;

    private double hp;
    private int pickaxeDurability;
    private int foodCount;

    public NPCGear() {
        this.hp = MAX_HP;
        this.pickaxeDurability = MAX_PICKAXE_DURABILITY;
        this.foodCount = 16;
    }

    public void takeDamage(double rawDamage) {
        double reduced = rawDamage * (1.0 - ARMOR_REDUCTION);
        hp = Math.max(0, hp - reduced);
    }

    public boolean tryEat() {
        if (foodCount <= 0) return false;
        foodCount--;
        hp = Math.min(MAX_HP, hp + FOOD_HEAL);
        return true;
    }

    public void usePickaxe() {
        if (pickaxeDurability > 0) {
            pickaxeDurability--;
        }
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public boolean isPickaxeBroken() {
        return pickaxeDurability <= 0;
    }

    public boolean shouldEat() {
        return hp < EAT_THRESHOLD && foodCount > 0;
    }

    public double getHp() { return hp; }
    public double getMaxHp() { return MAX_HP; }
    public int getPickaxeDurability() { return pickaxeDurability; }
    public int getFoodCount() { return foodCount; }
    public double getSwordDamage() { return SWORD_DAMAGE; }

    public String getHpDisplay() {
        return String.format("%.0f/%.0f", hp, MAX_HP);
    }
}
