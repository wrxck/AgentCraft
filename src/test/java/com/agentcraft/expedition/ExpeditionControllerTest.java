package com.agentcraft.expedition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.navigation.NavigationController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.testutil.BukkitTestSupport;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpeditionControllerTest {

    private TestWorld tw;
    private AIAgent agent;
    private BehaviorController behavior;
    private NavigationController nav;
    private FakePlayer npc;
    private Player owner;
    private Location home;

    private ExpeditionReporter reporter;
    private MacroNavigator macroNavigator;
    private ExpeditionCombatHandler combatHandler;
    private WaypointManager waypointManager;

    @BeforeEach
    void setUp() {
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        BukkitTestSupport.setServer(server);

        tw = new TestWorld();
        home = new Location(tw.world, 5, 64, 5);

        npc = mock(FakePlayer.class);
        when(npc.getLocation()).thenReturn(new Location(tw.world, 0, 64, 0));
        when(npc.getName()).thenReturn("Bot");

        nav = mock(NavigationController.class);
        behavior = mock(BehaviorController.class);
        when(behavior.getNavigation()).thenReturn(nav);
        when(behavior.getHomeLocation()).thenReturn(home);

        AgentCraftPlugin plugin = mock(AgentCraftPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ExpeditionControllerTest"));

        agent = mock(AIAgent.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getBehaviorController()).thenReturn(behavior);
        when(agent.getPlugin()).thenReturn(plugin);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());

        reporter = mock(ExpeditionReporter.class);
        macroNavigator = mock(MacroNavigator.class);
        combatHandler = mock(ExpeditionCombatHandler.class);
        waypointManager = mock(WaypointManager.class);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private ExpeditionController newController(String material, int count) throws Exception {
        ExpeditionController controller = new ExpeditionController(agent, owner, material, count);
        set(controller, "reporter", reporter);
        set(controller, "macroNavigator", macroNavigator);
        set(controller, "combatHandler", combatHandler);
        set(controller, "waypointManager", waypointManager);
        set(controller, "gear", new NPCGear());
        return controller;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = ExpeditionController.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setState(ExpeditionController controller, ExpeditionState state) throws Exception {
        set(controller, "state", state);
    }

    // --- Finding 4a / 8: category resolution ---

    @Test
    void categoryResolvesSpacedMaterialNames() throws Exception {
        assertEquals(MaterialCategory.UNDERGROUND_DEEP,
                newController("ancient debris", 4).getCategory());
    }

    // --- Finding 4d: token-based scanning ---

    @Test
    void scanUsesTokenMatching() throws Exception {
        tw.setDefault(Material.AIR);
        tw.setType(1, 64, 0, Material.REDSTONE_ORE); // closer, must not match
        tw.setType(1, 65, 0, Material.STONE);

        ExpeditionController controller = newController("stone", 8);
        Location found = controller.scanForMaterial(new Location(tw.world, 0, 64, 0), 1, 1);

        assertNotNull(found);
        assertEquals(1, found.getBlockX());
        assertEquals(65, found.getBlockY());
    }

    // --- Finding 4b: delayed collection task must respect expedition state ---

    @Test
    void delayedCollectionIsSkippedAfterExpeditionFailed() throws Exception {
        doReturn(List.of()).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        ExpeditionController controller = newController("iron_ore", 1);
        setState(controller, ExpeditionState.FAILED);

        controller.finishGatherCollection(new Location(tw.world, 0, 64, 0));

        assertEquals(ExpeditionState.FAILED, controller.getState(),
                "cancelled expedition must not be resurrected by the delayed task");
        assertEquals(0, controller.getGathered());
    }

    @Test
    void delayedCollectionDoesNotStompCombatState() throws Exception {
        doReturn(List.of()).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        ExpeditionController controller = newController("iron_ore", 1);
        setState(controller, ExpeditionState.COMBAT);

        controller.finishGatherCollection(new Location(tw.world, 0, 64, 0));

        assertEquals(ExpeditionState.COMBAT, controller.getState(),
                "delayed task must not yank the NPC out of combat");
    }

    @Test
    void delayedCollectionCompletesMissionInNominalFlow() throws Exception {
        doReturn(List.of()).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        ExpeditionController controller = newController("iron_ore", 1);
        setState(controller, ExpeditionState.GATHERING);

        controller.finishGatherCollection(new Location(tw.world, 0, 64, 0));

        assertEquals(1, controller.getGathered());
        assertEquals(ExpeditionState.RETURNING_HOME, controller.getState());
        verify(reporter).reportReturning(1, "IRON_ORE");
        verify(macroNavigator).navigateTo(home);
    }

    // --- Finding 4c: no item conjuring ---

    @Test
    void completeDoesNotConjureItemsWithoutCollectedBacking() throws Exception {
        ExpeditionController controller = newController("iron_ore", 2);
        set(controller, "gathered", 2);
        setState(controller, ExpeditionState.RETURNING_HOME);

        controller.complete();

        assertEquals(ExpeditionState.COMPLETED, controller.getState());
        // Nothing was actually vacuumed into the NPC inventory, so nothing
        // may be dropped at home.
        verify(tw.world, never()).dropItem(any(Location.class), any(ItemStack.class));
    }

    @Test
    void completeDropsExactlyTheCollectedInventoryBackedItems() throws Exception {
        // Two iron ore drops vacuumed during gathering.
        org.bukkit.entity.Item drop = mock(org.bukkit.entity.Item.class);
        when(drop.getItemStack()).thenReturn(new ItemStack(Material.IRON_ORE, 1));
        doReturn(List.of(drop)).when(tw.world)
                .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());

        ExpeditionController controller = newController("iron_ore", 5);
        setState(controller, ExpeditionState.GATHERING);
        controller.finishGatherCollection(new Location(tw.world, 0, 64, 0));
        controller.finishGatherCollection(new Location(tw.world, 0, 64, 0));

        when(behavior.removeFromInventory(Material.IRON_ORE, 2)).thenReturn(2);
        setState(controller, ExpeditionState.RETURNING_HOME);
        controller.complete();

        verify(behavior).removeFromInventory(Material.IRON_ORE, 2);
        verify(tw.world).dropItem(eq(home), org.mockito.ArgumentMatchers.<ItemStack>argThat(
                stack -> stack.getType() == Material.IRON_ORE && stack.getAmount() == 2));
        assertEquals(ExpeditionState.COMPLETED, controller.getState());
    }

    // --- Finding 3 (minor): kill report ---

    @Test
    void combatKillIsReportedWithMobName() throws Exception {
        ExpeditionController controller = newController("iron_ore", 4);
        setState(controller, ExpeditionState.COMBAT);
        when(combatHandler.isDone()).thenReturn(true);
        when(combatHandler.getCurrentTarget()).thenReturn(null); // already cleared internally
        when(combatHandler.consumeLastKillName()).thenReturn("zombie");

        controller.tick();

        verify(reporter).reportCombat(eq("zombie"), eq(true));
        assertEquals(ExpeditionState.SEARCHING, controller.getState());
    }

    // --- Finding 6: home teleport must update NPC position without viewers ---

    @Test
    void cancelAndTeleportHomeUpdatesNpcPositionWithoutViewers() throws Exception {
        ExpeditionController controller = newController("iron_ore", 4);
        setState(controller, ExpeditionState.SEARCHING);

        controller.cancelAndTeleportHome();

        assertEquals(ExpeditionState.FAILED, controller.getState());
        verify(npc).setLocation(home);
    }
}
