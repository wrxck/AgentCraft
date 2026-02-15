package com.agentcraft.npc;

import com.agentcraft.agent.AgentManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.BiConsumer;

public class NPCInteractListener {

    private final Plugin plugin;
    private final NPCTracker tracker;
    private BiConsumer<Player, FakePlayer> interactHandler;

    public NPCInteractListener(Plugin plugin, NPCTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    public void setInteractHandler(BiConsumer<Player, FakePlayer> handler) {
        this.interactHandler = handler;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        PacketContainer packet = event.getPacket();
                        int entityId = packet.getIntegers().read(0);

                        if (!tracker.isNPC(entityId)) return;

                        EnumWrappers.EntityUseAction action = packet.getEnumEntityUseActions().read(0).getAction();
                        if (action != EnumWrappers.EntityUseAction.INTERACT) return;

                        FakePlayer npc = tracker.getByEntityId(entityId);
                        Player player = event.getPlayer();

                        event.setCancelled(true);

                        // Dispatch to main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (interactHandler != null) {
                                interactHandler.accept(player, npc);
                            }
                        });
                    }
                }
        );
    }
}
