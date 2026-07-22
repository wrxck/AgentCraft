package com.agentcraft.listener;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentMentionParserTest {

    private static final List<String> AGENTS = List.of("Bob", "Alice");

    @Test
    void simpleMentionWithTask() {
        Optional<AgentMentionParser.Mention> m = AgentMentionParser.parse("Bob come here", AGENTS);
        assertTrue(m.isPresent());
        assertEquals("Bob", m.get().agentName());
        assertEquals("come here", m.get().task());
    }

    @Test
    void commaAndColonAfterNameAreStripped() {
        assertEquals("come here",
                AgentMentionParser.parse("bob, come here", AGENTS).orElseThrow().task());
        assertEquals("mine some iron",
                AgentMentionParser.parse("bob: mine some iron", AGENTS).orElseThrow().task());
    }

    @Test
    void matchingIsCaseInsensitive() {
        Optional<AgentMentionParser.Mention> m = AgentMentionParser.parse("BOB build a house", AGENTS);
        assertTrue(m.isPresent());
        assertEquals("Bob", m.get().agentName());
        assertEquals("build a house", m.get().task());
    }

    @Test
    void nameEmbeddedInAnotherWordIsNotAMention() {
        assertTrue(AgentMentionParser.parse("bobby is my friend", AGENTS).isEmpty());
        assertTrue(AgentMentionParser.parse("thingamabob is broken", AGENTS).isEmpty());
        assertTrue(AgentMentionParser.parse("bobbing for apples", AGENTS).isEmpty());
    }

    @Test
    void nameBoundedByPunctuationIsAMention() {
        assertTrue(AgentMentionParser.parse("hey bob!", AGENTS).isPresent());
        assertTrue(AgentMentionParser.parse("(bob) hello", AGENTS).isPresent());
        // Only a leading ',' or ':' is stripped (original listener semantics);
        // other punctuation stays part of the task text.
        assertEquals(") hello", AgentMentionParser.parse("(bob) hello", AGENTS).orElseThrow().task());
    }

    @Test
    void nameOnlyMessageHasEmptyTask() {
        Optional<AgentMentionParser.Mention> m = AgentMentionParser.parse("Bob", AGENTS);
        assertTrue(m.isPresent());
        assertEquals("", m.get().task());
    }

    @Test
    void trailingMentionHasEmptyTask() {
        Optional<AgentMentionParser.Mention> m = AgentMentionParser.parse("come here bob", AGENTS);
        assertTrue(m.isPresent());
        assertEquals("", m.get().task());
    }

    @Test
    void noMentionYieldsEmpty() {
        assertTrue(AgentMentionParser.parse("nice weather today", AGENTS).isEmpty());
        assertTrue(AgentMentionParser.parse("", AGENTS).isEmpty());
        assertTrue(AgentMentionParser.parse(null, AGENTS).isEmpty());
    }

    @Test
    void parseAllReturnsOneMentionPerAgentInOrder() {
        List<AgentMentionParser.Mention> all =
                AgentMentionParser.parseAll("bob and alice: dig a hole", AGENTS);
        assertEquals(2, all.size());
        assertEquals("Bob", all.get(0).agentName());
        assertEquals("and alice: dig a hole", all.get(0).task());
        assertEquals("Alice", all.get(1).agentName());
        assertEquals("dig a hole", all.get(1).task());
    }

    @Test
    void firstWordBoundaryOccurrenceWins() {
        // "bobby" at the start must be skipped in favour of the real mention.
        Optional<AgentMentionParser.Mention> m =
                AgentMentionParser.parse("bobby, tell bob to dig", AGENTS);
        assertTrue(m.isPresent());
        assertEquals("to dig", m.get().task());
    }
}
