package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.MaterialMatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Branch mining at target Y-level.
 * Mines a main 3x3 corridor forward, every 3 blocks digs perpendicular
 * 1x2 branches (16 blocks each side), scanning exposed walls for target material.
 */
public class BranchMiner {

    private static final int BRANCH_INTERVAL = 3;
    private static final int BRANCH_LENGTH = 16;
    private static final int SCAN_RADIUS = 2;
    private static final int MOVE_TICKS = 8;
    private static final int MAX_CORRIDOR_STEPS = 30;

    public enum State {
        IDLE, MINING_CORRIDOR, TURNING, MINING_BRANCH,
        SCANNING, FOUND_TARGET, EXHAUSTED
    }

    private final AIAgent agent;
    private final BlockBreaker breaker;
    private final String targetMaterial;
    private final int dirX;
    private final int dirZ;
    private final int perpX;
    private final int perpZ;

    private State state = State.IDLE;
    private int corridorSteps;
    private int branchStep;
    private int branchSide; // +1 right, -1 left
    private Location targetLocation;

    // Mining sub-state
    private Location[] faceBlocks;
    private int faceIndex;

    // Branch 1x2 mining
    private Location[] branchBlocks;
    private int branchBlockIndex;

    // Smooth movement
    private double moveDx, moveDy, moveDz;
    private float moveYaw;
    private int moveTicksRemaining;
    private Location moveTarget;
    private State afterMove;

    public BranchMiner(AIAgent agent, NPCGear gear, String targetMaterial,
                       int dirX, int dirZ, int perpX, int perpZ) {
        this.agent = agent;
        this.breaker = new BlockBreaker(agent, gear);
        this.targetMaterial = targetMaterial.toUpperCase().replace(' ', '_');
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.perpX = perpX;
        this.perpZ = perpZ;
    }

    public void start() {
        state = State.MINING_CORRIDOR;
        corridorSteps = 0;
        branchSide = 1;
        calculateCorridorFace();
    }

    public void tick() {
        if (state == State.IDLE || state == State.FOUND_TARGET || state == State.EXHAUSTED) return;

        switch (state) {
            case MINING_CORRIDOR -> tickMiningCorridor();
            case TURNING -> tickMoving();
            case MINING_BRANCH -> tickMiningBranch();
            case SCANNING -> tickScanning();
            default -> {}
        }
    }

    private void calculateCorridorFace() {
        Location npcLoc = agent.getNpc().getLocation();
        // 3x3 face at current Y level (no descent)
        int[][] coords = MiningFaces.flatFace(
                npcLoc.getBlockX(), npcLoc.getBlockY(), npcLoc.getBlockZ(),
                dirX, dirZ, perpX, perpZ);

        faceBlocks = new Location[coords.length];
        for (int i = 0; i < coords.length; i++) {
            faceBlocks[i] = new Location(npcLoc.getWorld(), coords[i][0], coords[i][1], coords[i][2]);
        }
        faceIndex = 0;
        breaker.reset();
    }

    private void tickMiningCorridor() {
        // Find next solid block to mine
        while (faceIndex < faceBlocks.length) {
            Block block = faceBlocks[faceIndex].getBlock();
            if (block.getType().isSolid()) break;
            faceIndex++;
        }

        if (faceIndex >= faceBlocks.length) {
            // Face done, move forward
            corridorSteps++;

            if (corridorSteps >= MAX_CORRIDOR_STEPS) {
                state = State.EXHAUSTED;
                return;
            }

            // Start smooth move forward (flat, no descent)
            Location npcLoc = agent.getNpc().getLocation();
            double tx = npcLoc.getX() + dirX;
            double tz = npcLoc.getZ() + dirZ;
            moveTarget = new Location(npcLoc.getWorld(), tx, npcLoc.getY(), tz);
            startSmoothMove(moveTarget, corridorSteps % BRANCH_INTERVAL == 0
                    ? State.SCANNING : State.MINING_CORRIDOR);
            return;
        }

        Location blockLoc = faceBlocks[faceIndex];
        if (breaker.tickBreakBlock(blockLoc)) {
            faceIndex++;
            breaker.reset();
        }
    }

    private void tickScanning() {
        // Scan exposed walls in both perpendicular directions
        Location npcLoc = agent.getNpc().getLocation();
        Location found = scanForTarget(npcLoc);
        if (found != null) {
            targetLocation = found.clone();
            state = State.FOUND_TARGET;
            return;
        }

        // Start branch mining
        branchStep = 0;
        branchSide = 1;
        startBranch();
    }

    private void startBranch() {
        Location npcLoc = agent.getNpc().getLocation();
        int bDirX = perpX * branchSide;
        int bDirZ = perpZ * branchSide;

        // 1x2 branch: blocks at feet and head level in the branch direction
        int bx = npcLoc.getBlockX() + bDirX;
        int by = npcLoc.getBlockY();
        int bz = npcLoc.getBlockZ() + bDirZ;

        branchBlocks = new Location[]{
                new Location(npcLoc.getWorld(), bx, by, bz),
                new Location(npcLoc.getWorld(), bx, by + 1, bz)
        };
        branchBlockIndex = 0;
        breaker.reset();
        state = State.MINING_BRANCH;
    }

    private void tickMiningBranch() {
        // Find next solid block
        while (branchBlockIndex < branchBlocks.length) {
            if (branchBlocks[branchBlockIndex].getBlock().getType().isSolid()) break;
            branchBlockIndex++;
        }

        if (branchBlockIndex >= branchBlocks.length) {
            branchStep++;

            // Scan after each step
            Location npcLoc = agent.getNpc().getLocation();
            Location found = scanForTarget(npcLoc);
            if (found != null) {
                targetLocation = found;
                state = State.FOUND_TARGET;
                return;
            }

            if (branchStep >= BRANCH_LENGTH) {
                // Branch done, switch side or return to corridor
                if (branchSide == 1) {
                    // Return to corridor then mine left branch
                    returnToCorridor();
                    branchSide = -1;
                    branchStep = 0;
                    startBranch();
                    return;
                } else {
                    // Both branches done, return and continue corridor
                    returnToCorridor();
                    state = State.MINING_CORRIDOR;
                    calculateCorridorFace();
                    return;
                }
            }

            // Move forward into branch
            int bDirX = perpX * branchSide;
            int bDirZ = perpZ * branchSide;
            Location target = npcLoc.clone().add(bDirX, 0, bDirZ);
            startSmoothMove(target, State.MINING_BRANCH);

            // Prepare next branch blocks
            int bx = target.getBlockX() + bDirX;
            int by = target.getBlockY();
            int bz = target.getBlockZ() + bDirZ;
            branchBlocks = new Location[]{
                    new Location(npcLoc.getWorld(), bx, by, bz),
                    new Location(npcLoc.getWorld(), bx, by + 1, bz)
            };
            branchBlockIndex = 0;
            breaker.reset();
            return;
        }

        Location blockLoc = branchBlocks[branchBlockIndex];
        if (breaker.tickBreakBlock(blockLoc)) {
            branchBlockIndex++;
            breaker.reset();
        }
    }

    void returnToCorridor() {
        breaker.cancelBreakAnimation();
        // Teleport back to corridor position (branches are exploratory)
        Location npcLoc = agent.getNpc().getLocation();
        int bDirX = perpX * branchSide;
        int bDirZ = perpZ * branchSide;

        // Calculate corridor center: go back branchStep steps
        double cx = npcLoc.getX() - bDirX * branchStep;
        double cz = npcLoc.getZ() - bDirZ * branchStep;
        Location corridorPos = new Location(npcLoc.getWorld(), cx, npcLoc.getY(), cz);

        FakePlayer npc = agent.getNpc();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.teleport(viewer, corridorPos);
        }
        // Update the NPC's internal position exactly once, even when no
        // players are online to receive teleport packets.
        npc.setLocation(corridorPos);
    }

    Location scanForTarget(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        Location nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (MaterialMatcher.matches(b.getType(), targetMaterial)) {
                        double distSq = b.getLocation().distanceSquared(center);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = b.getLocation();
                        }
                    }
                }
            }
        }

        return nearest;
    }

    private void startSmoothMove(Location target, State nextState) {
        Location npcLoc = agent.getNpc().getLocation();
        moveDx = (target.getX() - npcLoc.getX()) / MOVE_TICKS;
        moveDy = (target.getY() - npcLoc.getY()) / MOVE_TICKS;
        moveDz = (target.getZ() - npcLoc.getZ()) / MOVE_TICKS;

        double ddx = target.getX() - npcLoc.getX();
        double ddz = target.getZ() - npcLoc.getZ();
        moveYaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));

        moveTarget = target;
        moveTicksRemaining = MOVE_TICKS;
        afterMove = nextState;
        state = State.TURNING;
    }

    private void tickMoving() {
        if (moveTicksRemaining <= 0) {
            FakePlayer npc = agent.getNpc();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.teleport(viewer, moveTarget);
            }
            // Update the NPC's internal position exactly once, even when no
            // players are online to receive teleport packets.
            npc.setLocation(moveTarget);

            state = afterMove;
            if (state == State.MINING_CORRIDOR) {
                calculateCorridorFace();
            }
            return;
        }

        FakePlayer npc = agent.getNpc();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.move(viewer, moveDx, moveDy, moveDz, moveYaw, 0);
        }
        npc.updatePosition(moveDx, moveDy, moveDz, moveYaw, 0);
        moveTicksRemaining--;
    }

    public State getState() { return state; }
    public boolean isFoundTarget() { return state == State.FOUND_TARGET; }
    public boolean isExhausted() { return state == State.EXHAUSTED; }
    public Location getTargetLocation() { return targetLocation; }
}
