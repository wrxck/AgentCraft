package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentcraft.tool.impl.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ToolPromptBuilderTest {

    /** Registers every tool exactly as AgentCraftPlugin.onEnable does. */
    private static ToolRegistry pluginRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new GotoTool());
        registry.register(new FollowTool());
        registry.register(new FleeTool());
        registry.register(new AttackTool());
        registry.register(new MineTool());
        registry.register(new GatherTool());
        registry.register(new PlaceTool());
        registry.register(new LookAtTool());
        registry.register(new ApproachTool());
        registry.register(new WanderTool());
        registry.register(new GoHomeTool());
        registry.register(new IdleTool());
        registry.register(new EatTool());
        registry.register(new DropTool());
        registry.register(new ExpeditionTool());
        registry.register(new ScanAreaTool());
        registry.register(new GetPositionTool());
        registry.register(new CheckInventoryTool());
        registry.register(new CheckBlockTool());
        registry.register(new BreakBlockTool());
        registry.register(new PlaceBlockTool());
        registry.register(new CraftTool());
        registry.register(new InteractBlockTool());
        registry.register(new GiveItemTool());
        registry.register(new SetSignTool());
        registry.register(new BuildStructureTool());
        registry.register(new StoreItemsTool());
        registry.register(new EmoteTool());
        registry.register(new EquipTool());
        registry.register(new SmeltTool());
        registry.register(new FarmTool());
        registry.register(new BreedTool());
        registry.register(new SleepTool());
        registry.register(new AskHelpTool());
        return registry;
    }

    private static Set<String> catalogToolNames() {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^([a-z_]+)\\(").matcher(ToolPromptBuilder.compactToolCatalog());
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @Test
    void compactPromptListsExactlyTheRegisteredTools() {
        Set<String> registered = new LinkedHashSet<>();
        for (MinecraftTool tool : pluginRegistry().getAll()) {
            registered.add(tool.getName());
        }

        Set<String> advertised = catalogToolNames();

        Set<String> missing = new LinkedHashSet<>(registered);
        missing.removeAll(advertised);
        Set<String> phantom = new LinkedHashSet<>(advertised);
        phantom.removeAll(registered);

        assertTrue(missing.isEmpty(), "registered tools missing from compact prompt: " + missing);
        assertTrue(phantom.isEmpty(), "compact prompt advertises unregistered tools: " + phantom);
    }

    @Test
    void lookAtAdvertisesOnlyThePlayerParameterItImplements() {
        String catalog = ToolPromptBuilder.compactToolCatalog();
        String lookAtLine = catalog.lines()
                .filter(l -> l.startsWith("look_at("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("look_at missing from compact prompt"));

        assertTrue(lookAtLine.startsWith("look_at(player)"),
                "look_at only reads 'player'; prompt must match: " + lookAtLine);
        assertFalse(lookAtLine.contains("x y z"),
                "look_at does not accept coordinates: " + lookAtLine);
    }

    @Test
    void catalogAdvertisesEveryParamKeyAdvertisedToolsActuallyDeclare() {
        // Sanity: the golden set stays stable in size so accidental deletions show up.
        assertEquals(pluginRegistry().getAll().size(), catalogToolNames().size(),
                "compact prompt tool count must match registry");
    }
}
