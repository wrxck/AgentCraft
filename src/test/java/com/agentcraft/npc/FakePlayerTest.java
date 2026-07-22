package com.agentcraft.npc;

import com.agentcraft.testutil.BukkitTestSupport;
import com.agentcraft.util.LocationUtil;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FakePlayerTest {

    private MockedStatic<ProtocolLibrary> protocolLib;
    private ProtocolManager manager;
    private List<PacketContainer> sent;
    private Plugin plugin;
    private World world;
    private Player viewer;

    @BeforeEach
    void setUp() {
        manager = mock(ProtocolManager.class);
        sent = new ArrayList<>();
        doAnswer(inv -> {
            sent.add(inv.getArgument(1));
            return null;
        }).when(manager).sendServerPacket(any(), any());

        protocolLib = mockStatic(ProtocolLibrary.class);
        protocolLib.when(ProtocolLibrary::getProtocolManager).thenReturn(manager);

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        BukkitTestSupport.setServer(server);

        plugin = mock(Plugin.class);
        world = mock(World.class);
        viewer = mock(Player.class);
    }

    @AfterEach
    void tearDown() {
        protocolLib.close();
        BukkitTestSupport.clearServer();
    }

    private static byte toAngle(float degrees) {
        return (byte) (degrees * 256.0F / 360.0F);
    }

    private PacketContainer packetOfType(PacketType type) {
        return sent.stream().filter(p -> p.getType() == type).findFirst().orElse(null);
    }

    @Test
    void entityIdsStartOutsideRealisticServerRangeAndAreUnique() {
        FakePlayer a = new FakePlayer(plugin, "A", new Location(world, 0, 65, 0));
        FakePlayer b = new FakePlayer(plugin, "B", new Location(world, 0, 65, 0));

        // Real servers allocate entity IDs from a counter starting near 0; a
        // long-running server can reach the hundreds of thousands, so the fake
        // ID space must start far above anything realistically reachable.
        assertTrue(a.getEntityId() >= 1_900_000_000,
                "entity ID " + a.getEntityId() + " can collide with real server entity IDs");
        assertTrue(b.getEntityId() >= 1_900_000_000);
        assertNotEquals(a.getEntityId(), b.getEntityId());
    }

    @Test
    void spawnEntityPacketWritesPitchThenYaw() {
        Location loc = new Location(world, 1.0, 65.0, 2.0, 90.0f, 45.0f);
        FakePlayer npc = new FakePlayer(plugin, "SpawnBot", loc);
        npc.spawn(viewer);

        PacketContainer spawn = packetOfType(PacketType.Play.Server.SPAWN_ENTITY);
        assertNotNull(spawn, "SPAWN_ENTITY packet must be sent");
        // Modern SPAWN_ENTITY byte order: index 0 = pitch (xRot), index 1 = yaw (yRot),
        // index 2 = head yaw.
        assertEquals(toAngle(45.0f), spawn.getBytes().read(0), "byte 0 must be pitch");
        assertEquals(toAngle(90.0f), spawn.getBytes().read(1), "byte 1 must be yaw");
        assertEquals(toAngle(90.0f), spawn.getBytes().read(2), "byte 2 must be head yaw");
    }

    @Test
    void setPoseSleepingWritesSleepingPoseMetadata() {
        FakePlayer npc = new FakePlayer(plugin, "Sleeper", new Location(world, 0, 65, 0));
        npc.setPose(viewer, true);

        PacketContainer metadata = packetOfType(PacketType.Play.Server.ENTITY_METADATA);
        assertNotNull(metadata);
        List<WrappedDataValue> values = metadata.getDataValueCollectionModifier().read(0);
        WrappedDataValue pose = values.stream().filter(v -> v.getIndex() == 6).findFirst().orElse(null);
        assertNotNull(pose, "pose metadata (index 6) must be written");
        assertEquals(EnumWrappers.EntityPose.SLEEPING, pose.getValue());
    }

    @Test
    void setPoseStandingWritesStandingPoseMetadata() {
        FakePlayer npc = new FakePlayer(plugin, "Stander", new Location(world, 0, 65, 0));
        npc.setPose(viewer, false);

        PacketContainer metadata = packetOfType(PacketType.Play.Server.ENTITY_METADATA);
        assertNotNull(metadata);
        List<WrappedDataValue> values = metadata.getDataValueCollectionModifier().read(0);
        WrappedDataValue pose = values.stream().filter(v -> v.getIndex() == 6).findFirst().orElse(null);
        assertNotNull(pose, "pose metadata (index 6) must be written");
        assertEquals(EnumWrappers.EntityPose.STANDING, pose.getValue());
    }

    @Test
    void lookAtMatchesLocationUtilMath() {
        Location from = new Location(world, 0.0, 65.0, 0.0);
        FakePlayer npc = new FakePlayer(plugin, "Looker", from);
        Location target = new Location(world, 3.0, 66.0, 4.0);

        float[] expected = LocationUtil.calculateYawPitch(from, target);
        npc.lookAt(viewer, target);

        PacketContainer look = packetOfType(PacketType.Play.Server.ENTITY_LOOK);
        assertNotNull(look);
        assertEquals(toAngle(expected[0]), look.getBytes().read(0));
        assertEquals(toAngle(expected[1]), look.getBytes().read(1));
        assertEquals(expected[0], npc.getLocation().getYaw(), 1e-6);
        assertEquals(expected[1], npc.getLocation().getPitch(), 1e-6);
    }

    @Test
    void spawnSchedulesTabListRemoval() {
        FakePlayer npc = new FakePlayer(plugin, "TabBot", new Location(world, 0, 65, 0));
        npc.spawn(viewer);
        verify(org.bukkit.Bukkit.getScheduler()).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
    }
}
