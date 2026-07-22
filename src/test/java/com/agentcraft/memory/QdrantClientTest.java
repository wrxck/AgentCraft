package com.agentcraft.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class QdrantClientTest {

    private final QdrantClient client =
            new QdrantClient("localhost", 6333, "", Logger.getLogger("QdrantClientTest"));

    @Test
    void parsesNormalSearchResult() {
        String body = """
                {"result":[
                  {"id":"abc-123","score":0.87,"payload":{
                    "agent_name":"grumble","player_name":"Steve",
                    "summary":"Talked about mining","timestamp":1720000000000}},
                  {"id":"def-456","score":0.42,"payload":{
                    "agent_name":"grumble","player_name":"Alex",
                    "summary":"Built a hut","timestamp":1720000100000}}
                ]}
                """;

        List<MemoryEntry> entries = client.parseSearchResults(body);

        assertEquals(2, entries.size());
        MemoryEntry first = entries.get(0);
        assertEquals("abc-123", first.id());
        assertEquals("grumble", first.agentName());
        assertEquals("Steve", first.playerName());
        assertEquals("Talked about mining", first.summary());
        assertEquals(1720000000000L, first.timestamp());
        assertEquals(0.87f, first.score(), 0.0001f);
    }

    @Test
    void missingFieldsDefaultInsteadOfThrowing() {
        String body = """
                {"result":[{"payload":{}}]}
                """;

        List<MemoryEntry> entries = client.parseSearchResults(body);

        assertEquals(1, entries.size());
        MemoryEntry entry = entries.get(0);
        assertEquals("", entry.id());
        assertEquals("", entry.agentName());
        assertEquals("", entry.playerName());
        assertEquals("", entry.summary());
        assertEquals(0L, entry.timestamp());
        assertEquals(0f, entry.score());
    }

    @Test
    void missingPayloadDefaults() {
        List<MemoryEntry> entries = client.parseSearchResults("{\"result\":[{\"id\":\"x\",\"score\":1.0}]}");
        assertEquals(1, entries.size());
        assertEquals("x", entries.get(0).id());
        assertEquals("", entries.get(0).summary());
    }

    @Test
    void malformedJsonReturnsEmptyList() {
        assertTrue(client.parseSearchResults("this is not json{{{").isEmpty());
        assertTrue(client.parseSearchResults("null").isEmpty());
    }

    @Test
    void emptyOrAbsentResultReturnsEmptyList() {
        assertTrue(client.parseSearchResults("{\"result\":[]}").isEmpty());
        assertTrue(client.parseSearchResults("{\"status\":\"ok\"}").isEmpty());
    }
}
