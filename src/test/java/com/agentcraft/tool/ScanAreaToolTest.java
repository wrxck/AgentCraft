package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.ScanAreaTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScanAreaToolTest {

    private final ScanAreaTool tool = new ScanAreaTool();
    private AIAgent agent;
    private World world;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        agent = InventoryAgentMock.create(new ArrayList<>(), world, loc);
        when(world.getNearbyEntities(any(Location.class), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(List.of());
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void negativeRadiusIsClampedToOne() {
        ToolResult result = tool.execute(agent, params("{\"target\":\"players\",\"radius\":-5}"));

        assertTrue(result.success(), result.message());
        verify(world).getNearbyEntities(any(Location.class), eq(1.0), eq(1.0), eq(1.0));
    }

    @Test
    void oversizedRadiusIsClampedToThirtyTwo() {
        ToolResult result = tool.execute(agent, params("{\"target\":\"players\",\"radius\":500}"));

        assertTrue(result.success(), result.message());
        verify(world).getNearbyEntities(any(Location.class), eq(32.0), eq(32.0), eq(32.0));
    }
}
