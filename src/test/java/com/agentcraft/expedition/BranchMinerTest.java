package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class BranchMinerTest {

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
        Location npcLoc = new Location(tw.world, 0.5, 12, 0.5, 0f, 0f);
        when(npc.getLocation()).thenAnswer(inv -> npcLoc.clone());
        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        gear = new NPCGear();
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private BranchMiner newMiner(String target) {
        // Mining south (dirX=0, dirZ=1), perp (-1, 0).
        return new BranchMiner(agent, gear, target, 0, 1, -1, 0);
    }

    @Test
    void scanIgnoresPartialTokenMatches() {
        // Looking for STONE: REDSTONE_ORE (closer) and COBBLESTONE (ambient)
        // must not match; the real STONE block further away must be found.
        tw.setDefault(Material.COBBLESTONE);
        tw.setType(1, 12, 0, Material.REDSTONE_ORE);
        tw.setType(2, 12, 0, Material.STONE);

        BranchMiner miner = newMiner("stone");
        Location found = miner.scanForTarget(new Location(tw.world, 0, 12, 0));

        assertNotNull(found);
        assertEquals(2, found.getBlockX());
        assertEquals(12, found.getBlockY());
        assertEquals(0, found.getBlockZ());
    }

    @Test
    void scanFindsNothingWhenOnlyPartialMatchesPresent() {
        tw.setDefault(Material.AIR);
        tw.setType(1, 12, 0, Material.REDSTONE_ORE);
        tw.setType(-1, 12, 0, Material.GLOWSTONE);

        BranchMiner miner = newMiner("stone");
        assertNull(miner.scanForTarget(new Location(tw.world, 0, 12, 0)));
    }

    @Test
    void scanFindsNearestRealMatch() {
        tw.setDefault(Material.AIR);
        tw.setType(2, 12, 0, Material.DEEPSLATE_IRON_ORE);
        tw.setType(1, 12, 0, Material.IRON_ORE);

        BranchMiner miner = newMiner("iron ore");
        Location found = miner.scanForTarget(new Location(tw.world, 0, 12, 0));
        assertNotNull(found);
        assertEquals(1, found.getBlockX());
    }

    @Test
    void returnToCorridorUpdatesInternalPositionEvenWithNoViewers() {
        BranchMiner miner = newMiner("iron_ore");
        miner.returnToCorridor();
        // With zero players online the NPC must still be moved internally.
        verify(npc).setLocation(any(Location.class));
    }
}
