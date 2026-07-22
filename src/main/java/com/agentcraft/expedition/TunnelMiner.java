package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Digs a 3x3 staircase tunnel down to a target Y-level.
 * Uses smooth movement (move packets, not teleports) and sweep-pattern mining.
 * Detects caves when 6+ of the 9 face blocks are air.
 */
public class TunnelMiner {

    private static final int TORCH_INTERVAL = 8;
    private static final int CAVE_AIR_THRESHOLD = 6;
    private static final int MOVE_TICKS = 8;

    public enum State { IDLE, MINING_FACE, MOVING_FORWARD, PLACING_TORCH, ARRIVED, CAVE_FOUND }

    private final AIAgent agent;
    private final BlockBreaker breaker;
    private final int targetY;
    private final int dirX;
    private final int dirZ;
    private final int perpX; // Perpendicular direction for 3-wide spread
    private final int perpZ;

    private State state = State.IDLE;
    private int blocksMined;
    private Location caveLocation;

    // 3x3 face mining
    private Location[] faceBlocks;
    private int faceIndex;
    private int faceInitialAirCount;

    // Smooth movement
    private double moveDx, moveDy, moveDz;
    private float moveYaw, movePitch;
    private int moveTicksRemaining;
    private Location moveTarget;

    public TunnelMiner(AIAgent agent, NPCGear gear, int targetY) {
        this.agent = agent;
        this.breaker = new BlockBreaker(agent, gear);
        this.targetY = targetY;

        // Pick a consistent cardinal direction based on NPC yaw
        float yaw = agent.getNpc().getLocation().getYaw();
        if (yaw < -135 || yaw >= 135) {
            dirX = 0; dirZ = -1; // North
            perpX = 1; perpZ = 0;
        } else if (yaw >= -135 && yaw < -45) {
            dirX = 1; dirZ = 0; // East
            perpX = 0; perpZ = 1;
        } else if (yaw >= -45 && yaw < 45) {
            dirX = 0; dirZ = 1; // South
            perpX = -1; perpZ = 0;
        } else {
            dirX = -1; dirZ = 0; // West
            perpX = 0; perpZ = -1;
        }
    }

    public void start() {
        state = State.MINING_FACE;
        blocksMined = 0;
        calculateFaceBlocks();
    }

    public void tick() {
        if (state == State.IDLE || state == State.ARRIVED || state == State.CAVE_FOUND) return;

        Location npcLoc = agent.getNpc().getLocation();

        // Check if we've reached target Y
        if (npcLoc.getBlockY() <= targetY) {
            breaker.cancelBreakAnimation();
            state = State.ARRIVED;
            return;
        }

        switch (state) {
            case MINING_FACE -> tickMiningFace();
            case MOVING_FORWARD -> tickMovingForward();
            case PLACING_TORCH -> tickPlacingTorch(npcLoc);
            default -> {}
        }
    }

    /**
     * Calculate the 9 block positions for the 3x3 cross-section ahead.
     * The cross-section is 3 wide (perpendicular) x 3 tall.
     * The staircase descends: the bottom row is at npcY-1, middle at npcY, top at npcY+1.
     */
    private void calculateFaceBlocks() {
        Location npcLoc = agent.getNpc().getLocation();
        int[][] coords = MiningFaces.descendingFace(
                npcLoc.getBlockX(), npcLoc.getBlockY(), npcLoc.getBlockZ(),
                dirX, dirZ, perpX, perpZ);

        faceBlocks = new Location[coords.length];
        for (int i = 0; i < coords.length; i++) {
            faceBlocks[i] = new Location(npcLoc.getWorld(), coords[i][0], coords[i][1], coords[i][2]);
        }
        faceIndex = 0;
        breaker.reset();

        // Snapshot how many face blocks are open BEFORE we mine any of them.
        // Cave detection must only consider pre-existing air; otherwise the
        // blocks we break ourselves would trip the threshold on every face.
        faceInitialAirCount = 0;
        for (Location loc : faceBlocks) {
            if (!loc.getBlock().getType().isSolid()) faceInitialAirCount++;
        }
    }

    private void tickMiningFace() {
        // Check for cave: how many of the 9 face blocks were already open
        // when the face was computed (self-mined blocks excluded).
        if (faceInitialAirCount >= CAVE_AIR_THRESHOLD) {
            breaker.cancelBreakAnimation();
            // Cave entrance center
            Location center = faceBlocks[4]; // middle block
            caveLocation = new Location(center.getWorld(),
                    center.getBlockX() + 0.5, center.getBlockY(), center.getBlockZ() + 0.5);
            state = State.CAVE_FOUND;
            return;
        }

        // Find next solid block to mine
        while (faceIndex < faceBlocks.length) {
            Block block = faceBlocks[faceIndex].getBlock();
            if (block.getType().isSolid()) break;
            faceIndex++;
        }

        if (faceIndex >= faceBlocks.length) {
            // All blocks in face are mined, move forward
            blocksMined += 9;
            startMovingForward();
            return;
        }

        // Mine the current block
        Location blockLoc = faceBlocks[faceIndex];
        if (breaker.tickBreakBlock(blockLoc)) {
            faceIndex++;
            breaker.reset();
        }
    }

    private void startMovingForward() {
        breaker.cancelBreakAnimation();
        Location npcLoc = agent.getNpc().getLocation();
        double tx = npcLoc.getBlockX() + dirX + 0.5;
        double ty = npcLoc.getBlockY() - 1;
        double tz = npcLoc.getBlockZ() + dirZ + 0.5;

        moveTarget = new Location(npcLoc.getWorld(), tx, ty, tz);
        moveDx = (moveTarget.getX() - npcLoc.getX()) / MOVE_TICKS;
        moveDy = (moveTarget.getY() - npcLoc.getY()) / MOVE_TICKS;
        moveDz = (moveTarget.getZ() - npcLoc.getZ()) / MOVE_TICKS;

        // Calculate yaw facing dig direction
        moveYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        movePitch = 0;
        moveTicksRemaining = MOVE_TICKS;
        state = State.MOVING_FORWARD;
    }

    private void tickMovingForward() {
        if (moveTicksRemaining <= 0) {
            // Snap to final position
            FakePlayer npc = agent.getNpc();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.teleport(viewer, moveTarget);
            }
            // Update the NPC's internal position exactly once, even when no
            // players are online to receive teleport packets.
            npc.setLocation(moveTarget);

            // Place torch every N blocks
            if (blocksMined % (TORCH_INTERVAL * 9) == 0 && blocksMined > 0) {
                state = State.PLACING_TORCH;
            } else {
                state = State.MINING_FACE;
                calculateFaceBlocks();
            }
            return;
        }

        FakePlayer npc = agent.getNpc();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.move(viewer, moveDx, moveDy, moveDz, moveYaw, movePitch);
        }
        npc.updatePosition(moveDx, moveDy, moveDz, moveYaw, movePitch);
        moveTicksRemaining--;
    }

    private void tickPlacingTorch(Location npcLoc) {
        World world = npcLoc.getWorld();
        int tx = npcLoc.getBlockX();
        int ty = npcLoc.getBlockY() + 1;
        int tz = npcLoc.getBlockZ();

        // Try to place torch on a wall (check behind, left, right)
        int[][] wallOffsets = {{-dirX, -dirZ}, {perpX, perpZ}, {-perpX, -perpZ}};
        for (int[] off : wallOffsets) {
            Block wall = world.getBlockAt(tx + off[0], ty, tz + off[1]);
            if (wall.getType().isSolid()) {
                Block torchBlock = world.getBlockAt(tx, ty, tz);
                if (torchBlock.getType().isAir()) {
                    torchBlock.setType(Material.TORCH);
                }
                break;
            }
        }

        state = State.MINING_FACE;
        calculateFaceBlocks();
    }

    public State getState() { return state; }
    public boolean isArrived() { return state == State.ARRIVED; }
    public boolean isCaveFound() { return state == State.CAVE_FOUND; }
    public Location getCaveLocation() { return caveLocation; }
    public int getDirX() { return dirX; }
    public int getDirZ() { return dirZ; }
    public int getPerpX() { return perpX; }
    public int getPerpZ() { return perpZ; }
}
