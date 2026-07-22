package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TunnelMinerTest {

    private TestWorld tw;
    private AIAgent agent;
    private FakePlayer npc;
    private NPCGear gear;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        tw = new TestWorld();
        npc = mock(FakePlayer.class);
        // Yaw 0 -> digging south (dirX=0, dirZ=1), perp (-1, 0).
        Location npcLoc = new Location(tw.world, 0.5, 70, 0.5, 0f, 0f);
        when(npc.getLocation()).thenAnswer(inv -> npcLoc.clone());
        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        gear = new NPCGear();
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    /** Face blocks for the south-facing miner at (0,70,0): x in -1..1, y in 69..71, z=1. */
    private void fillFace(Material material) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 69; y <= 71; y++) {
                tw.setType(x, y, 1, material);
            }
        }
    }

    @Test
    void fullySolidFaceIsMinedCompletelyWithoutFalseCaveDetection() {
        fillFace(Material.STONE);
        tw.setDefault(Material.STONE);

        TunnelMiner miner = new TunnelMiner(agent, gear, 10);
        miner.start();

        int guard = 0;
        while (miner.getState() == TunnelMiner.State.MINING_FACE) {
            miner.tick();
            // The miner must never mistake its own freshly-broken blocks
            // for a pre-existing cave.
            assertNotEquals(TunnelMiner.State.CAVE_FOUND, miner.getState(),
                    "cave falsely detected from self-mined air");
            guard++;
            if (guard > 500) {
                throw new AssertionError("face never completed; state=" + miner.getState());
            }
        }

        assertEquals(TunnelMiner.State.MOVING_FORWARD, miner.getState());
        for (int x = -1; x <= 1; x++) {
            for (int y = 69; y <= 71; y++) {
                assertEquals(Material.AIR, tw.getType(x, y, 1), "block not mined at " + x + "," + y);
            }
        }
    }

    @Test
    void genuinelyOpenFaceTriggersCaveFound() {
        fillFace(Material.STONE);
        tw.setDefault(Material.STONE);
        // 6 of the 9 face blocks are open from the start.
        for (int x = -1; x <= 1; x++) {
            tw.setType(x, 70, 1, Material.CAVE_AIR);
            tw.setType(x, 71, 1, Material.CAVE_AIR);
        }

        TunnelMiner miner = new TunnelMiner(agent, gear, 10);
        miner.start();
        miner.tick();

        assertEquals(TunnelMiner.State.CAVE_FOUND, miner.getState());
        assertNotNull(miner.getCaveLocation());
    }

    @Test
    void forwardSnapUpdatesInternalPositionEvenWithNoViewers() {
        fillFace(Material.STONE);
        tw.setDefault(Material.STONE);

        TunnelMiner miner = new TunnelMiner(agent, gear, 10);
        miner.start();

        int guard = 0;
        while (miner.getState() == TunnelMiner.State.MINING_FACE && guard++ < 500) {
            miner.tick();
        }
        assertEquals(TunnelMiner.State.MOVING_FORWARD, miner.getState());

        // 8 smooth-move ticks plus the snap tick.
        for (int i = 0; i < 9; i++) {
            miner.tick();
        }

        // With zero players online, the NPC's internal position must still
        // be updated when the move completes.
        verify(npc).setLocation(any(Location.class));
    }
}
