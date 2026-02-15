package com.agentcraft.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FakePlayer {

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(100000);
    private static final int SKIN_LAYERS_INDEX = 17;
    private static final byte SKIN_LAYERS_ALL = 0x7F;

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final WrappedGameProfile profile;
    private final Plugin plugin;

    private Location location;
    private SkinData skinData;
    private boolean spawned;

    public FakePlayer(Plugin plugin, String name, Location location) {
        this.plugin = plugin;
        this.entityId = ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.location = location.clone();
        this.skinData = SkinData.EMPTY;
        this.profile = new WrappedGameProfile(uuid, name);
        this.spawned = false;
    }

    public void setSkinData(SkinData skinData) {
        this.skinData = skinData;
        if (!skinData.isEmpty()) {
            profile.getProperties().put("textures",
                    new WrappedSignedProperty("textures", skinData.value(), skinData.signature()));
        }
    }

    public void spawn(Player viewer) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // 1. Player Info (add to tab temporarily for skin)
        sendPlayerInfo(viewer, pm);

        // 2. Spawn Entity
        sendSpawnEntity(viewer, pm);

        // 3. Entity Metadata (skin layers)
        sendMetadata(viewer, pm);

        // 4. Head Rotation
        sendHeadRotation(viewer, pm, location.getYaw());

        // 5. Remove from tab after delay (skin needs ~2 seconds to load)
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPlayerInfoRemove(viewer, pm), 40L);

        this.spawned = true;
    }

    public void despawn(Player viewer) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer destroy = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, List.of(entityId));
        pm.sendServerPacket(viewer, destroy);

        // Also remove from tab in case still listed
        sendPlayerInfoRemove(viewer, pm);
    }

    public void move(Player viewer, double dx, double dy, double dz, float yaw, float pitch) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        packet.getIntegers().write(0, entityId);
        packet.getShorts()
                .write(0, (short) (dx * 4096))
                .write(1, (short) (dy * 4096))
                .write(2, (short) (dz * 4096));
        packet.getBytes()
                .write(0, toAngle(yaw))
                .write(1, toAngle(pitch));
        packet.getBooleans().write(0, true); // on ground

        pm.sendServerPacket(viewer, packet);
        sendHeadRotation(viewer, pm, yaw);
    }

    /**
     * Update internal tracked position. Call once after broadcasting move() to all viewers.
     */
    public void updatePosition(double dx, double dy, double dz, float yaw, float pitch) {
        location.add(dx, dy, dz);
        location.setYaw(yaw);
        location.setPitch(pitch);
    }

    public void breakBlockAnimation(Player viewer, Location blockLoc, int stage) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        packet.getIntegers().write(0, entityId);
        packet.getBlockPositionModifier().write(0, new BlockPosition(
                blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ()));
        packet.getIntegers().write(1, stage);
        pm.sendServerPacket(viewer, packet);
    }

    public void teleport(Player viewer, Location to) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        packet.getIntegers().write(0, entityId);
        packet.getDoubles()
                .write(0, to.getX())
                .write(1, to.getY())
                .write(2, to.getZ());
        packet.getBytes()
                .write(0, toAngle(to.getYaw()))
                .write(1, toAngle(to.getPitch()));
        packet.getBooleans().write(0, true); // on ground

        pm.sendServerPacket(viewer, packet);
        sendHeadRotation(viewer, pm, to.getYaw());

        this.location = to.clone();
    }

    public void lookAt(Player viewer, Location target) {
        double dx = target.getX() - location.getX();
        double dy = target.getY() - location.getY();
        double dz = target.getZ() - location.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        // Send entity look packet
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_LOOK);
        packet.getIntegers().write(0, entityId);
        packet.getBytes()
                .write(0, toAngle(yaw))
                .write(1, toAngle(pitch));
        packet.getBooleans().write(0, true);

        pm.sendServerPacket(viewer, packet);
        sendHeadRotation(viewer, pm, yaw);

        location.setYaw(yaw);
        location.setPitch(pitch);
    }

    public void swingArm(Player viewer) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ANIMATION);
        packet.getIntegers().write(0, entityId);
        packet.getIntegers().write(1, 0); // main arm swing
        pm.sendServerPacket(viewer, packet);
    }

    public void hurtAnimation(Player viewer) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);
        packet.getIntegers().write(0, entityId);
        packet.getBytes().write(0, (byte) 2); // hurt effect
        pm.sendServerPacket(viewer, packet);
    }

    public void sneak(Player viewer, boolean sneaking) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();

        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);

        // Entity base metadata index 0: flags byte
        // Bit 0x02 = crouching
        byte flags = sneaking ? (byte) 0x02 : (byte) 0x00;

        List<WrappedDataValue> values = List.of(
                new WrappedDataValue(0, byteSerializer, flags),
                new WrappedDataValue(SKIN_LAYERS_INDEX, byteSerializer, SKIN_LAYERS_ALL)
        );

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        packet.getDataValueCollectionModifier().write(0, values);
        pm.sendServerPacket(viewer, packet);
    }

    // --- Private helpers ---

    private void sendPlayerInfo(Player viewer, ProtocolManager pm) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);

        EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED
        );
        packet.getPlayerInfoActions().write(0, actions);

        PlayerInfoData data = new PlayerInfoData(
                uuid,
                0,     // latency
                true,  // listed (temporarily)
                EnumWrappers.NativeGameMode.SURVIVAL,
                profile,
                WrappedChatComponent.fromText(name)
        );
        packet.getPlayerInfoDataLists().write(1, List.of(data));

        pm.sendServerPacket(viewer, packet);
    }

    private void sendPlayerInfoRemove(Player viewer, ProtocolManager pm) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, List.of(uuid));
        pm.sendServerPacket(viewer, packet);
    }

    private void sendSpawnEntity(Player viewer, ProtocolManager pm) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        packet.getIntegers().write(0, entityId);
        packet.getUUIDs().write(0, uuid);
        packet.getEntityTypeModifier().write(0, EntityType.PLAYER);
        packet.getDoubles()
                .write(0, location.getX())
                .write(1, location.getY())
                .write(2, location.getZ());
        packet.getBytes()
                .write(0, toAngle(location.getYaw()))
                .write(1, toAngle(location.getPitch()))
                .write(2, toAngle(location.getYaw())); // head yaw
        pm.sendServerPacket(viewer, packet);
    }

    private void sendMetadata(Player viewer, ProtocolManager pm) {
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);

        List<WrappedDataValue> values = List.of(
                new WrappedDataValue(SKIN_LAYERS_INDEX, byteSerializer, SKIN_LAYERS_ALL)
        );

        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        packet.getDataValueCollectionModifier().write(0, values);
        pm.sendServerPacket(viewer, packet);
    }

    private void sendHeadRotation(Player viewer, ProtocolManager pm, float yaw) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        packet.getIntegers().write(0, entityId);
        packet.getBytes().write(0, toAngle(yaw));
        pm.sendServerPacket(viewer, packet);
    }

    private static byte toAngle(float degrees) {
        return (byte) (degrees * 256.0F / 360.0F);
    }

    // --- Getters ---

    public int getEntityId() { return entityId; }
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public Location getLocation() { return location.clone(); }
    public boolean isSpawned() { return spawned; }

    public void setLocation(Location location) {
        this.location = location.clone();
    }
}
