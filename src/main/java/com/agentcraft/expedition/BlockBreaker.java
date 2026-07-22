package com.agentcraft.expedition;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Shared block-breaking sub-state used by {@link TunnelMiner} and
 * {@link BranchMiner}: a staged break animation advanced one tick at a time,
 * followed by the actual block break.
 */
class BlockBreaker {

    private static final int BREAK_STAGES = 10;
    private static final int TICKS_PER_STAGE = 2; // Faster with Efficiency V

    private final AIAgent agent;
    private final NPCGear gear;

    private int breakStage = -1;
    private int stageCooldown;
    private Location currentBreakLoc;

    BlockBreaker(AIAgent agent, NPCGear gear) {
        this.agent = agent;
        this.gear = gear;
    }

    /** Reset break progress (e.g. when moving on to a new face or block). */
    void reset() {
        breakStage = -1;
        stageCooldown = 0;
    }

    /**
     * Advance the break of the block at the given location by one tick.
     * Returns true once the block has been broken.
     */
    boolean tickBreakBlock(Location blockLoc) {
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
            reset();
            return true;
        }
    }

    void cancelBreakAnimation() {
        if (currentBreakLoc != null) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                agent.getNpc().breakBlockAnimation(viewer, currentBreakLoc, -1);
            }
            currentBreakLoc = null;
        }
    }
}
