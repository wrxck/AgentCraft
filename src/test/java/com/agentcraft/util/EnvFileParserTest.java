package com.agentcraft.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvFileParserTest {

    @Test
    void parsesBasicKeyValuePairs() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "POSTGRES_HOST=db",
                "POSTGRES_PORT=5432"));
        assertEquals("db", result.get("POSTGRES_HOST"));
        assertEquals("5432", result.get("POSTGRES_PORT"));
    }

    @Test
    void skipsCommentsAndBlankLines() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "# a comment",
                "",
                "   ",
                "  # indented comment",
                "KEY=value"));
        assertEquals(1, result.size());
        assertEquals("value", result.get("KEY"));
    }

    @Test
    void trimsKeysAndValues() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "  KEY  =  value  "));
        assertEquals("value", result.get("KEY"));
    }

    @Test
    void stripsSurroundingDoubleQuotes() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "QDRANT_API_KEY=\"s3cr3t-key\"",
                "PLAIN=unquoted"));
        assertEquals("s3cr3t-key", result.get("QDRANT_API_KEY"),
                "quoted values must be unquoted for every consumer");
        assertEquals("unquoted", result.get("PLAIN"));
    }

    @Test
    void keepsEqualsSignsInsideValues() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "TOKEN=abc=def=="));
        assertEquals("abc=def==", result.get("TOKEN"));
    }

    @Test
    void skipsMalformedLines() {
        Map<String, String> result = EnvFileParser.parse(List.of(
                "no-equals-here",
                "=value-without-key",
                "GOOD=yes"));
        assertEquals(1, result.size());
        assertEquals("yes", result.get("GOOD"));
    }

    @Test
    void emptyValueIsAllowed() {
        Map<String, String> result = EnvFileParser.parse(List.of("EMPTY="));
        assertTrue(result.containsKey("EMPTY"));
        assertEquals("", result.get("EMPTY"));
    }

    @Test
    void singleQuoteCharValueDoesNotCrash() {
        Map<String, String> result = EnvFileParser.parse(List.of("ODD=\""));
        assertEquals("\"", result.get("ODD"));
    }

    @Test
    void readerOverloadMatchesListOverload() throws IOException {
        String file = """
                # secrets
                QDRANT_HOST=qdrant
                QDRANT_API_KEY="quoted"
                """;
        Map<String, String> fromReader = EnvFileParser.parse(new StringReader(file));
        assertEquals("qdrant", fromReader.get("QDRANT_HOST"));
        assertEquals("quoted", fromReader.get("QDRANT_API_KEY"));
        assertFalse(fromReader.containsKey("# secrets"));
    }
}
