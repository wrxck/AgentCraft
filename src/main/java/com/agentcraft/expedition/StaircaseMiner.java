package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Digs a 1x2 staircase down to a target Y-level.
 * Detects caves (large air pockets) while digging.
 */
public class StaircaseMiner {

    private static final int BREAK_STAGES = 10;
    private static final int TICKS_PER_STAGE = 3;
    private static final int TORCH_INTERVAL = 8;
    private static final int CAVE_AIR_THRESHOLD = 3;

    public enum State { IDLE, MINING_HEAD, MINING_FEET, STEPPING, PLACING_TORCH, ARRIVED, CAVE_FOUND }

    private final AIAgent agent;
    private final NPCGear gear;
    private final int targetY;
    private final int dirX;
    private final int dirZ;

    private State state = State.IDLE;
    private int breakStage = -1;
    private int stageCooldown;
    private int blocksMined;
    private Location currentBreakLoc;
    private Location caveLocation;

    public StaircaseMiner(AIAgent agent, NPCGear gear, int targetY) {
        this.agent = agent;
        this.gear = gear;
        this.targetY = targetY;

        // Pick a consistent direction (cardinal) based on NPC yaw
        float yaw = agent.getNpc().getLocation().getYaw();
        if (yaw < -135 || yaw >= 135) {
            dirX = 0; dirZ = -1; // North
        } else if (yaw >= -135 && yaw < -45) {
            dirX = 1; dirZ = 0; // East
        } else if (yaw >= -45 && yaw < 45) {
            dirX = 0; dirZ = 1; // South
        } else {
            dirX = -1; dirZ = 0; // West
        }
    }

    public void start() {
        state = State.MINING_HEAD;
        breakStage = -1;
        stageCooldown = 0;
        blocksMined = 0;
    }

    public void tick() {
        if (state == State.IDLE || state == State.ARRIVED || state == State.CAVE_FOUND) return;

        FakePlayer npc = agent.getNpc();
        Location npcLoc = npc.getLocation();

        // Check if we've reached target Y
        if (npcLoc.getBlockY() <= targetY) {
            cancelBreakAnimation();
            state = State.ARRIVED;
            return;
        }

        // Check if pickaxe is broken
        if (gear.isPickaxeBroken()) {
            cancelBreakAnimation();
            state = State.ARRIVED; // treat as done — can't dig further
            return;
        }

        switch (state) {
            case MINING_HEAD -> tickMiningHead(npcLoc);
            case MINING_FEET -> tickMiningFeet(npcLoc);
            case STEPPING -> tickStepping(npcLoc);
            case PLACING_TORCH -> tickPlacingTorch(npcLoc);
            default -> {}
        }
    }

    private void tickMiningHead(Location npcLoc) {
        // Mine block at head level in front, one step down
        int bx = npcLoc.getBlockX() + dirX;
        int by = npcLoc.getBlockY(); // head level of the step-down position
        int bz = npcLoc.getBlockZ() + dirZ;

        Location blockLoc = new Location(npcLoc.getWorld(), bx, by, bz);
        Block block = blockLoc.getBlock();

        if (block.getType().isAir() || !block.getType().isSolid()) {
            // Check for cave: count air below/ahead
            if (checkForCave(npcLoc)) {
                state = State.CAVE_FOUND;
                return;
            }
            state = State.MINING_FEET;
            breakStage = -1;
            stageCooldown = 0;
            return;
        }

        if (tickBreakBlock(blockLoc)) {
            state = State.MINING_FEET;
            breakStage = -1;
            stageCooldown = 0;
        }
    }

    private void tickMiningFeet(Location npcLoc) {
        // Mine block at feet level in front, one step down
        int bx = npcLoc.getBlockX() + dirX;
        int by = npcLoc.getBlockY() - 1; // feet level of step-down position
        int bz = npcLoc.getBlockZ() + dirZ;

        Location blockLoc = new Location(npcLoc.getWorld(), bx, by, bz);
        Block block = blockLoc.getBlock();

        if (block.getType().isAir() || !block.getType().isSolid()) {
            if (checkForCave(npcLoc)) {
                state = State.CAVE_FOUND;
                return;
            }
            state = State.STEPPING;
            return;
        }

        if (tickBreakBlock(blockLoc)) {
            state = State.STEPPING;
        }
    }

    private void tickStepping(Location npcLoc) {
        // Move NPC forward and down one block
        double tx = npcLoc.getBlockX() + dirX + 0.5;
        double ty = npcLoc.getBlockY() - 1;
        double tz = npcLoc.getBlockZ() + dirZ + 0.5;

        Location target = new Location(npcLoc.getWorld(), tx, ty, tz);

        // Teleport NPC to the new position
        FakePlayer npc = agent.getNpc();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.teleport(viewer, target);
        }

        blocksMined += 2;

        // Place torch every N blocks
        if (blocksMined % TORCH_INTERVAL == 0) {
            state = State.PLACING_TORCH;
        } else {
            state = State.MINING_HEAD;
            breakStage = -1;
            stageCooldown = 0;
        }
    }

    private void tickPlacingTorch(Location npcLoc) {
        // Place a torch at the NPC's position on the wall
        World world = npcLoc.getWorld();
        int tx = npcLoc.getBlockX();
        int ty = npcLoc.getBlockY() + 1;
        int tz = npcLoc.getBlockZ();

        // Try to place torch on a wall
        int[][] wallOffsets = {{-dirX, -dirZ}, {dirZ, -dirX}, {-dirZ, dirX}};
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

        state = State.MINING_HEAD;
        breakStage = -1;
        stageCooldown = 0;
    }

    private boolean tickBreakBlock(Location blockLoc) {
        FakePlayer npc = agent.getNpc();

        // Look at the block
        Location blockCenter = blockLoc.clone().add(0.5, 0.5, 0.5);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            npc.lookAt(viewer, blockCenter);
        }

        if (stageCooldown > 0) {
            stageCooldown--;
            return false;
        }

        breakStage++;
        stageCooldown = TICKS_PER_STAGE;

        if (breakStage < BREAK_STAGES) {
            currentBreakLoc = blockLoc;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                npc.swingArm(viewer);
                npc.breakBlockAnimation(viewer, blockLoc, breakStage);
            }
            return false;
        } else {
            // Break complete
            cancelBreakAnimation();
            blockLoc.getBlock().breakNaturally();
            gear.usePickaxe();
            currentBreakLoc = null;
            return true;
        }
    }

    private boolean checkForCave(Location npcLoc) {
        World world = npcLoc.getWorld();
        int bx = npcLoc.getBlockX() + dirX;
        int bz = npcLoc.getBlockZ() + dirZ;
        int by = npcLoc.getBlockY() - 1;

        int airCount = 0;
        // Check below and ahead
        for (int dy = -3; dy <= 0; dy++) {
            Block b = world.getBlockAt(bx, by + dy, bz);
            if (!b.getType().isSolid()) airCount++;
        }
        // Check further ahead
        for (int d = 1; d <= 2; d++) {
            Block b = world.getBlockAt(bx + dirX * d, by, bz + dirZ * d);
            if (!b.getType().isSolid()) airCount++;
        }

        if (airCount >= CAVE_AIR_THRESHOLD) {
            caveLocation = new Location(world, bx + 0.5, by, bz + 0.5);
            return true;
        }
        return false;
    }

    private void cancelBreakAnimation() {
        if (currentBreakLoc != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                agent.getNpc().breakBlockAnimation(viewer, currentBreakLoc, -1);
            }
            currentBreakLoc = null;
        }
    }

    public State getState() { return state; }
    public boolean isArrived() { return state == State.ARRIVED; }
    public boolean isCaveFound() { return state == State.CAVE_FOUND; }
    public Location getCaveLocation() { return caveLocation; }
    public int getBlocksMined() { return blocksMined; }
}
