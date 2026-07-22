package com.agentcraft.npc;

import com.agentcraft.testutil.BukkitTestSupport;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NPCInteractListenerTest {

    private org.mockito.MockedStatic<ProtocolLibrary> protocolLib;
    private PacketAdapter adapter;
    private NPCTracker tracker;
    private FakePlayer npc;
    private Player player;
    private List<Object[]> handled;

    @BeforeEach
    void setUp() {
        ProtocolManager manager = mock(ProtocolManager.class);
        protocolLib = mockStatic(ProtocolLibrary.class);
        protocolLib.when(ProtocolLibrary::getProtocolManager).thenReturn(manager);

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        // Run scheduled tasks immediately.
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        });
        BukkitTestSupport.setServer(server);

        Plugin plugin = mock(Plugin.class);
        tracker = new NPCTracker();
        npc = new FakePlayer(plugin, "ClickMe", new Location(mock(World.class), 0, 65, 0));
        tracker.register(npc);

        handled = new ArrayList<>();
        NPCInteractListener listener = new NPCInteractListener(plugin, tracker);
        listener.setInteractHandler((p, n) -> handled.add(new Object[]{p, n}));
        listener.register();

        ArgumentCaptor<PacketListener> captor = ArgumentCaptor.forClass(PacketListener.class);
        verify(manager).addPacketListener(captor.capture());
        adapter = (PacketAdapter) captor.getValue();

        player = mock(Player.class);
    }

    @AfterEach
    void tearDown() {
        protocolLib.close();
        BukkitTestSupport.clearServer();
    }

    @SuppressWarnings("unchecked")
    private PacketEvent useEntityEvent(int entityId, EnumWrappers.EntityUseAction action, EnumWrappers.Hand hand) {
        PacketContainer packet = mock(PacketContainer.class);
        StructureModifier<Integer> ints = mock(StructureModifier.class);
        when(ints.read(0)).thenReturn(entityId);
        when(packet.getIntegers()).thenReturn(ints);

        WrappedEnumEntityUseAction useAction = mock(WrappedEnumEntityUseAction.class);
        when(useAction.getAction()).thenReturn(action);
        when(useAction.getHand()).thenReturn(hand);
        StructureModifier<WrappedEnumEntityUseAction> actions = mock(StructureModifier.class);
        when(actions.read(0)).thenReturn(useAction);
        when(packet.getEnumEntityUseActions()).thenReturn(actions);

        PacketEvent event = mock(PacketEvent.class);
        when(event.getPacket()).thenReturn(packet);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    @Test
    void mainHandInteractDispatchesHandlerOnce() {
        PacketEvent event = useEntityEvent(npc.getEntityId(),
                EnumWrappers.EntityUseAction.INTERACT, EnumWrappers.Hand.MAIN_HAND);
        adapter.onPacketReceiving(event);

        assertEquals(1, handled.size());
        assertSame(player, handled.get(0)[0]);
        assertSame(npc, handled.get(0)[1]);
        verify(event).setCancelled(true);
    }

    @Test
    void offHandInteractIsIgnoredSoRightClickFiresOnlyOnce() {
        // Vanilla clients send USE_ENTITY INTERACT once per hand for a single
        // right-click; only the MAIN_HAND packet may trigger the handler.
        adapter.onPacketReceiving(useEntityEvent(npc.getEntityId(),
                EnumWrappers.EntityUseAction.INTERACT, EnumWrappers.Hand.MAIN_HAND));
        adapter.onPacketReceiving(useEntityEvent(npc.getEntityId(),
                EnumWrappers.EntityUseAction.INTERACT, EnumWrappers.Hand.OFF_HAND));

        assertEquals(1, handled.size(), "OFF_HAND duplicate must be filtered out");
    }

    @Test
    void attackActionIsIgnored() {
        adapter.onPacketReceiving(useEntityEvent(npc.getEntityId(),
                EnumWrappers.EntityUseAction.ATTACK, EnumWrappers.Hand.MAIN_HAND));
        assertTrue(handled.isEmpty());
    }

    @Test
    void nonNpcEntityIsIgnored() {
        adapter.onPacketReceiving(useEntityEvent(12345,
                EnumWrappers.EntityUseAction.INTERACT, EnumWrappers.Hand.MAIN_HAND));
        assertTrue(handled.isEmpty());
    }
}
