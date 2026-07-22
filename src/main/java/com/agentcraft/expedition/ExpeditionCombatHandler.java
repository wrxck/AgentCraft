package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles combat during expeditions. Fights 1-2 mobs, flees from 3+.
 * Simulates incoming damage since mobs can't target FakePlayers.
 */
public class ExpeditionCombatHandler {

    private static final int SCAN_RANGE = 10;
    private static final int ATTACK_COOLDOWN_TICKS = 15;
    private static final double ATTACK_REACH = 2.5;
    private static final int FLEE_DISTANCE = 20;
    private static final int DAMAGE_RANGE = 3;

    private static final Map<EntityType, Double> MOB_DAMAGE = new EnumMap<>(EntityType.class);
    static {
        MOB_DAMAGE.put(EntityType.ZOMBIE, 3.0);
        MOB_DAMAGE.put(EntityType.HUSK, 3.0);
        MOB_DAMAGE.put(EntityType.DROWNED, 3.0);
        MOB_DAMAGE.put(EntityType.SKELETON, 4.0);
        MOB_DAMAGE.put(EntityType.STRAY, 4.0);
        MOB_DAMAGE.put(EntityType.SPIDER, 2.0);
        MOB_DAMAGE.put(EntityType.CAVE_SPIDER, 2.0);
        MOB_DAMAGE.put(EntityType.CREEPER, 6.0);
        MOB_DAMAGE.put(EntityType.WITCH, 3.0);
        MOB_DAMAGE.put(EntityType.ENDERMAN, 5.0);
        MOB_DAMAGE.put(EntityType.BLAZE, 4.0);
        MOB_DAMAGE.put(EntityType.WITHER_SKELETON, 5.0);
        MOB_DAMAGE.put(EntityType.PILLAGER, 3.0);
        MOB_DAMAGE.put(EntityType.VINDICATOR, 5.0);
    }

    public enum CombatResult { NONE, FIGHT, FLEE }

    private final AIAgent agent;
    private final NPCGear gear;

    private LivingEntity currentTarget;
    private boolean fighting;
    private boolean fleeing;
    private int attackCooldown;
    private Location fleeTarget;
    private boolean fleeArrived;
    private boolean fleeFailed;
    private int killCount;
    private String lastKillName;
    private int lastThreatCount;

    public ExpeditionCombatHandler(AIAgent agent, NPCGear gear) {
        this.agent = agent;
        this.gear = gear;
    }

    /**
     * Scan for threats and decide whether to fight or flee.
     * Returns NONE if no threats detected.
     */
    public CombatResult scan() {
        if (fighting || fleeing) return fighting ? CombatResult.FIGHT : CombatResult.FLEE;

        Location npcLoc = agent.getNpc().getLocation();
        List<LivingEntity> threats = new ArrayList<>();

        for (Entity entity : npcLoc.getWorld().getNearbyEntities(npcLoc, SCAN_RANGE, SCAN_RANGE, SCAN_RANGE)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity instanceof Player) continue;
            if (!isHostile(entity.getType())) continue;
            threats.add(living);
        }

        if (threats.isEmpty()) return CombatResult.NONE;

        lastThreatCount = threats.size();

        if (threats.size() >= 3) {
            startFleeing(npcLoc, threats.get(0).getLocation());
            return CombatResult.FLEE;
        }

        // Fight closest
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity mob : threats) {
            double dist = mob.getLocation().distanceSquared(npcLoc);
            if (dist < closestDist) {
                closestDist = dist;
                closest = mob;
            }
        }
        startFighting(closest);
        return CombatResult.FIGHT;
    }

    public void tick() {
        if (fighting) {
            tickFighting();
        } else if (fleeing) {
            tickFleeing();
        }

        // Simulate incoming damage from nearby hostile mobs
        simulateIncomingDamage();
    }

    public boolean isInCombat() {
        return fighting || fleeing;
    }

    public boolean isDone() {
        return !fighting && !fleeing;
    }

    public void cancel() {
        fighting = false;
        fleeing = false;
        currentTarget = null;
        fleeTarget = null;
        agent.getBehaviorController().getNavigation().cancel();
    }

    private void startFighting(LivingEntity target) {
        this.currentTarget = target;
        this.fighting = true;
        this.fleeing = false;
        this.attackCooldown = 0;

        // Navigate to target
        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(target.getLocation());
    }

    private void tickFighting() {
        if (currentTarget == null || currentTarget.isDead()) {
            if (currentTarget != null && currentTarget.isDead()) {
                registerKill();
            }
            fighting = false;
            currentTarget = null;
            agent.getBehaviorController().getNavigation().cancel();
            return;
        }

        FakePlayer npc = agent.getNpc();
        double dist = npc.getLocation().distance(currentTarget.getLocation());

        if (dist > ATTACK_REACH) {
            // Keep chasing
            NavigationController nav = agent.getBehaviorController().getNavigation();
            if (!nav.isNavigating()) {
                // Navigation was cancelled (e.g. after closing to attack
                // range) and the target escaped again: restart the chase,
                // updateGoalIfMoved() alone is a no-op while idle.
                nav.navigateTo(currentTarget.getLocation());
            } else {
                nav.updateGoalIfMoved(currentTarget.getLocation());
            }
            return;
        }

        // In range — cancel nav, face target, attack
        agent.getBehaviorController().getNavigation().cancel();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, currentTarget.getEyeLocation());
        }

        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        attackCooldown = ATTACK_COOLDOWN_TICKS;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.swingArm(viewer);
        }
        currentTarget.damage(gear.getSwordDamage());

        if (currentTarget.isDead()) {
            registerKill();
            fighting = false;
            currentTarget = null;
        }
    }

    private void registerKill() {
        killCount++;
        lastKillName = currentTarget.getType().name().toLowerCase();
    }

    private void startFleeing(Location npcLoc, Location threatLoc) {
        fleeing = true;
        fighting = false;
        currentTarget = null;

        double dx = npcLoc.getX() - threatLoc.getX();
        double dz = npcLoc.getZ() - threatLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double fx, fz;
        if (dist > 0.1) {
            fx = npcLoc.getX() + (dx / dist) * FLEE_DISTANCE;
            fz = npcLoc.getZ() + (dz / dist) * FLEE_DISTANCE;
        } else {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            fx = npcLoc.getX() + Math.cos(angle) * FLEE_DISTANCE;
            fz = npcLoc.getZ() + Math.sin(angle) * FLEE_DISTANCE;
        }

        fleeTarget = LocationUtil.findSafeGround(
                new Location(npcLoc.getWorld(), fx, npcLoc.getY(), fz));
        fleeArrived = false;
        fleeFailed = false;

        NavigationController nav = agent.getBehaviorController().getNavigation();
        nav.navigateTo(fleeTarget, () -> fleeArrived = true, () -> fleeFailed = true);
    }

    private void tickFleeing() {
        if (fleeArrived || fleeFailed
                || !agent.getBehaviorController().getNavigation().isNavigating()) {
            fleeing = false;
            fleeTarget = null;
        }
    }

    private void simulateIncomingDamage() {
        Location npcLoc = agent.getNpc().getLocation();

        for (Entity entity : npcLoc.getWorld().getNearbyEntities(npcLoc, DAMAGE_RANGE, DAMAGE_RANGE, DAMAGE_RANGE)) {
            if (entity instanceof Player) continue;
            if (!isHostile(entity.getType())) continue;

            Double damage = MOB_DAMAGE.get(entity.getType());
            if (damage == null) damage = 2.0;

            // Only apply damage randomly (~25% chance per tick when in range)
            // to avoid instant death from proximity
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                gear.takeDamage(damage);

                // Show hurt animation
                FakePlayer npc = agent.getNpc();
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    npc.hurtAnimation(viewer);
                }
                break; // Only take damage from one mob per tick
            }
        }
    }

    private boolean isHostile(EntityType type) {
        return MOB_DAMAGE.containsKey(type) || type == EntityType.SLIME
                || type == EntityType.MAGMA_CUBE || type == EntityType.PHANTOM;
    }

    public int getKillCount() { return killCount; }

    /**
     * Name of the most recently killed mob, or null. Clears on read, so the
     * caller reports each kill exactly once. (The target reference itself is
     * nulled before callers can observe the kill, hence this capture.)
     */
    public String consumeLastKillName() {
        String name = lastKillName;
        lastKillName = null;
        return name;
    }

    /** Number of threats seen by the most recent scan that found any. */
    public int getLastThreatCount() { return lastThreatCount; }

    public LivingEntity getCurrentTarget() { return currentTarget; }
    public boolean isFighting() { return fighting; }
    public boolean isFleeing() { return fleeing; }
}
