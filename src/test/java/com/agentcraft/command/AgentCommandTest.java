package com.agentcraft.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.npc.FakePlayer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentCommandTest {

    private AgentCommand command;
    private Player player;
    private Command bukkitCommand;
    private final List<String> injectedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        command = new AgentCommand(mock(AgentCraftPlugin.class));
        player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        bukkitCommand = mock(Command.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        Map<String, AIAgent> agents = agentsMap();
        for (String key : injectedKeys) {
            agents.remove(key);
        }
        injectedKeys.clear();
    }

    @SuppressWarnings("unchecked")
    private Map<String, AIAgent> agentsMap() throws Exception {
        Field field = AgentManager.class.getDeclaredField("agents");
        field.setAccessible(true);
        return (Map<String, AIAgent>) field.get(AgentManager.getInstance());
    }

    private void injectAgent(String canonicalName) throws Exception {
        AIAgent agent = mock(AIAgent.class);
        FakePlayer npc = mock(FakePlayer.class);
        when(agent.getNpc()).thenReturn(npc);
        when(npc.getName()).thenReturn(canonicalName);
        when(agent.getExpedition()).thenReturn(null);
        agentsMap().put(canonicalName.toLowerCase(), agent);
        injectedKeys.add(canonicalName.toLowerCase());
    }

    private List<String> messagesAfter(String... args) {
        command.onCommand(player, bukkitCommand, "agent", args);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(player, org.mockito.Mockito.atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void expeditionRejectsOversizedCount() throws Exception {
        injectAgent("Grumble");

        List<String> messages = messagesAfter("expedition", "Grumble", "stone", "999999");

        assertTrue(messages.stream().anyMatch(m -> m.contains("between 1 and 64")),
                "expected count bounds message, got: " + messages);
    }

    @Test
    void expeditionRejectsZeroAndNegativeCount() throws Exception {
        injectAgent("Grumble");

        List<String> zero = messagesAfter("expedition", "Grumble", "stone", "0");
        assertTrue(zero.stream().anyMatch(m -> m.contains("between 1 and 64")),
                "expected count bounds message, got: " + zero);

        List<String> negative = messagesAfter("expedition", "Grumble", "stone", "-3");
        assertTrue(negative.stream().anyMatch(m -> m.contains("between 1 and 64")),
                "expected count bounds message, got: " + negative);
    }

    @Test
    void expeditionRejectsNonNumericCount() throws Exception {
        injectAgent("Grumble");

        List<String> messages = messagesAfter("expedition", "Grumble", "stone", "lots");

        assertTrue(messages.stream().anyMatch(m -> m.contains("Invalid count")),
                "expected invalid count message, got: " + messages);
    }

    @Test
    void tabCompleteFiltersSubcommands() {
        List<String> result = command.onTabComplete(player, bukkitCommand, "agent",
                new String[] {"sp"});

        assertTrue(result.contains("spawn"));
        assertTrue(result.contains("spawnall"));
        assertFalse(result.contains("task"));
    }

    @Test
    void tabCompleteSuggestsAgentNames() throws Exception {
        injectAgent("Grumble");

        List<String> result = command.onTabComplete(player, bukkitCommand, "agent",
                new String[] {"despawn", "gr"});

        assertTrue(result.contains("Grumble"), "expected Grumble in " + result);
    }
}
