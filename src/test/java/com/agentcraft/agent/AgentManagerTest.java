package com.agentcraft.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.behavior.BehaviorController;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.npc.NPCTracker;
import com.agentcraft.persistence.PersistenceManager;
import com.agentcraft.testutil.BukkitTestSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentManagerTest {

    private AgentManager manager;
    private PersistenceManager persistence;

    @BeforeEach
    void setUp() throws Exception {
        BukkitTestSupport.setServer(mock(Server.class));

        Constructor<AgentManager> ctor = AgentManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        manager = ctor.newInstance();

        persistence = mock(PersistenceManager.class);
        when(persistence.isAvailable()).thenReturn(true);

        setField("plugin", mock(AgentCraftPlugin.class));
        setField("tracker", mock(NPCTracker.class));
        setField("persistence", persistence);
    }

    @AfterEach
    void tearDown() {
        BukkitTestSupport.clearServer();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AgentManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field field = AgentManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(manager);
    }

    private AIAgent registerAgent(String canonicalName, String skin) throws Exception {
        AIAgent agent = mock(AIAgent.class);
        FakePlayer npc = mock(FakePlayer.class);
        BehaviorController controller = mock(BehaviorController.class);
        AgentProfile profile = mock(AgentProfile.class);
        when(agent.getNpc()).thenReturn(npc);
        when(agent.getBehaviorController()).thenReturn(controller);
        when(agent.getProfile()).thenReturn(profile);
        when(npc.getName()).thenReturn(canonicalName);
        when(profile.getSkin()).thenReturn(skin);

        Map<String, AIAgent> agents = getField("agents");
        agents.put(canonicalName.toLowerCase(), agent);
        Set<String> usedNames = getField("usedNames");
        usedNames.add(canonicalName.toLowerCase());
        Set<String> usedSkins = getField("usedSkins");
        usedSkins.add(skin.toLowerCase());
        return agent;
    }

    @Test
    void despawnDeletesDatabaseRowUnderCanonicalNpcName() throws Exception {
        registerAgent("Grumble", "HermitSkin");

        boolean removed = manager.despawnAgent("grumble");

        assertTrue(removed);
        // DB rows are keyed by the NPC's canonical name, not the user-typed lowercase name.
        verify(persistence).deleteAgent("Grumble");
    }

    @Test
    void despawnKeepsSkinReservedWhileAnotherLiveAgentWearsIt() throws Exception {
        registerAgent("Grumble", "SharedSkin");
        registerAgent("Snarl", "SharedSkin");

        assertTrue(manager.despawnAgent("grumble"));

        Set<String> usedSkins = getField("usedSkins");
        assertTrue(usedSkins.contains("sharedskin"),
                "skin must stay reserved: Snarl still wears it");
    }

    @Test
    void despawnReleasesSkinWhenLastWearerLeaves() throws Exception {
        registerAgent("Grumble", "SoloSkin");

        assertTrue(manager.despawnAgent("grumble"));

        Set<String> usedSkins = getField("usedSkins");
        assertTrue(!usedSkins.contains("soloskin"), "skin should be released when unused");
    }
}
