package com.agentcraft.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.testutil.InventoryAgentMock;
import com.agentcraft.tool.impl.SetSignTool;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetSignToolTest {

    private final SetSignTool tool = new SetSignTool();
    private AIAgent agent;
    private World world;
    private Block block;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        agent = InventoryAgentMock.create(new ArrayList<>(), world, new Location(world, 0, 0, 0));
        block = mock(Block.class);
        when(world.getBlockAt(1, 0, 1)).thenReturn(block);
    }

    private static JsonObject params(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void refusesToOverwriteExistingBlock() {
        Chest chest = mock(Chest.class);
        when(block.getState()).thenReturn(chest);
        when(block.getType()).thenReturn(Material.CHEST);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"line1\":\"hello\"}"));

        assertFalse(result.success(), "must not overwrite a chest");
        assertTrue(result.message().toLowerCase().contains("already"),
                "expected already-a-block message, got: " + result.message());
        verify(block, never()).setType(any(Material.class));
    }

    @Test
    void placesSignIntoAir() {
        BlockState airState = mock(BlockState.class);
        Sign sign = mock(Sign.class);
        SignSide side = mock(SignSide.class);
        when(sign.getSide(Side.FRONT)).thenReturn(side);
        when(block.getState()).thenReturn(airState, sign);
        when(block.getType()).thenReturn(Material.AIR);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"line1\":\"hello\"}"));

        assertTrue(result.success(), result.message());
        verify(block).setType(Material.OAK_SIGN);
        verify(side).setLine(0, "hello");
        verify(sign).update();
    }

    @Test
    void editsExistingSignWithoutReplacingIt() {
        Sign sign = mock(Sign.class);
        SignSide side = mock(SignSide.class);
        when(sign.getSide(Side.FRONT)).thenReturn(side);
        when(block.getState()).thenReturn(sign);
        when(block.getType()).thenReturn(Material.OAK_SIGN);

        ToolResult result = tool.execute(agent,
                params("{\"x\":1,\"y\":0,\"z\":1,\"line1\":\"edited\"}"));

        assertTrue(result.success(), result.message());
        verify(block, never()).setType(any(Material.class));
        verify(side).setLine(0, "edited");
        verify(sign).update();
    }

    @Test
    void malformedCoordinateGivesFriendlyFailure() {
        ToolResult result = tool.execute(agent,
                params("{\"x\":\"abc\",\"y\":0,\"z\":1,\"line1\":\"hi\"}"));

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("coordinate"),
                "expected friendly coordinate message, got: " + result.message());
    }
}
