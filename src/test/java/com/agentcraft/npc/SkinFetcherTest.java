package com.agentcraft.npc;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkinFetcherTest {

    // --- parseSkinResponse ---

    @Test
    void parsesValueAndSignature() {
        String json = """
                {"id":"abc","name":"Steve","properties":[
                  {"name":"textures","value":"VAL","signature":"SIG"}
                ]}""";
        SkinData data = SkinFetcher.parseSkinResponse(new StringReader(json));
        assertEquals("VAL", data.value());
        assertEquals("SIG", data.signature());
        assertFalse(data.isEmpty());
    }

    @Test
    void missingSignatureYieldsEmptySignature() {
        String json = """
                {"id":"abc","name":"Steve","properties":[
                  {"name":"textures","value":"VAL"}
                ]}""";
        SkinData data = SkinFetcher.parseSkinResponse(new StringReader(json));
        assertEquals("VAL", data.value());
        assertEquals("", data.signature());
        assertTrue(data.isEmpty(), "unsigned skins count as empty");
    }

    @Test
    void emptyPropertiesYieldsEmpty() {
        String json = "{\"id\":\"abc\",\"name\":\"Steve\",\"properties\":[]}";
        assertTrue(SkinFetcher.parseSkinResponse(new StringReader(json)).isEmpty());
    }

    @Test
    void missingPropertiesYieldsEmpty() {
        String json = "{\"id\":\"abc\",\"name\":\"Steve\"}";
        assertTrue(SkinFetcher.parseSkinResponse(new StringReader(json)).isEmpty());
    }

    @Test
    void nonTexturesPropertiesAreIgnored() {
        String json = """
                {"properties":[
                  {"name":"other","value":"X","signature":"Y"},
                  {"name":"textures","value":"VAL","signature":"SIG"}
                ]}""";
        SkinData data = SkinFetcher.parseSkinResponse(new StringReader(json));
        assertEquals("VAL", data.value());
    }

    // --- caching semantics ---

    /** Test double that scripts resolveUUID/resolveTextures instead of doing HTTP. */
    private static class ScriptedSkinFetcher extends SkinFetcher {
        final AtomicInteger textureCalls = new AtomicInteger();
        volatile SkinData nextResult = SkinData.EMPTY;

        ScriptedSkinFetcher(Plugin plugin) {
            super(plugin);
        }

        @Override
        String resolveUUID(String username) {
            return "some-uuid";
        }

        @Override
        SkinData resolveTextures(String uuid) {
            textureCalls.incrementAndGet();
            return nextResult;
        }
    }

    @Test
    void transientEmptyResultIsNotCachedSoNextFetchRetries() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SkinFetcherTest"));
        ScriptedSkinFetcher fetcher = new ScriptedSkinFetcher(plugin);

        // First fetch: rate-limited/failed lookup -> EMPTY. Must not be cached.
        fetcher.nextResult = SkinData.EMPTY;
        assertTrue(fetcher.fetch("Steve").join().isEmpty());
        assertEquals(1, fetcher.textureCalls.get());
        assertTrue(fetcher.getCached("Steve").isEmpty());

        // Second fetch: Mojang recovered -> real skin, cached now.
        SkinData real = new SkinData("VAL", "SIG");
        fetcher.nextResult = real;
        assertEquals(real, fetcher.fetch("Steve").join());
        assertEquals(2, fetcher.textureCalls.get(), "an EMPTY result must not stick in the cache");
        assertEquals(real, fetcher.getCached("Steve"));

        // Third fetch: served from cache, no new lookup.
        assertEquals(real, fetcher.fetch("Steve").join());
        assertEquals(2, fetcher.textureCalls.get());
    }
}
