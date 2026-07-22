package com.agentcraft.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class AgentProfileTest {

    private static Plugin pluginWithYaml(String profileId, String yaml) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(new File("/nonexistent-agentcraft-test"));
        when(plugin.getResource("profiles/" + profileId + ".yml"))
                .thenAnswer(inv -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }

    @Test
    void emptyNamesAndSkinsListsFallBackToDefaults() {
        Plugin plugin = pluginWithYaml("hermit", """
                names: []
                skins: []
                personality: A test hermit.
                """);

        AgentProfile profile = AgentProfile.load(plugin, "hermit");

        assertNotNull(profile, "profile must load despite empty lists");
        assertEquals(List.of("hermit"), profile.getNames(), "empty names should fall back like a missing key");
        assertEquals(List.of("Steve"), profile.getSkins(), "empty skins should fall back like a missing key");
        assertEquals("hermit", profile.getName());
        assertEquals("Steve", profile.getSkin());
    }

    @Test
    void singleNameAndSkinFormIsSupported() {
        Plugin plugin = pluginWithYaml("guard", """
                name: Bob
                skin: Alex
                """);

        AgentProfile profile = AgentProfile.load(plugin, "guard");

        assertNotNull(profile);
        assertEquals(List.of("Bob"), profile.getNames());
        assertEquals(List.of("Alex"), profile.getSkins());
        assertEquals("Bob", profile.getName());
        assertEquals("Alex", profile.getSkin());
    }

    @Test
    void listFormIsSupported() {
        Plugin plugin = pluginWithYaml("miner", """
                names:
                  - Digsy
                  - Rocko
                skins:
                  - MinerSkin
                """);

        AgentProfile profile = AgentProfile.load(plugin, "miner");

        assertNotNull(profile);
        assertEquals(List.of("Digsy", "Rocko"), profile.getNames());
        assertEquals("Digsy", profile.getName());
        assertEquals(List.of("MinerSkin"), profile.getSkins());
    }

    @Test
    void withAssignmentCopiesProfileAndSetsRuntimeNameAndSkin() {
        Plugin plugin = pluginWithYaml("farmer", """
                names:
                  - Wheatley
                personality: Loves wheat.
                max-turns: 7
                max-spawns: 3
                """);

        AgentProfile base = AgentProfile.load(plugin, "farmer");
        AgentProfile assigned = base.withAssignment("Wheatley", "FarmSkin");

        assertEquals("Wheatley", assigned.getName());
        assertEquals("FarmSkin", assigned.getSkin());
        assertEquals("farmer", assigned.getId());
        assertEquals("Loves wheat.", assigned.getPersonality());
        assertEquals(7, assigned.getMaxTurns());
        assertEquals(3, assigned.getMaxSpawns());
        assertEquals(base.getNames(), assigned.getNames());
        // Base profile keeps its own runtime name
        assertEquals("Wheatley", base.getNames().get(0));
    }
}
