package com.agentcraft.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class MemoryManagerTest {

    private static long ago(long millis) {
        return System.currentTimeMillis() - millis;
    }

    @Test
    void availableFlagIsVolatileForCrossThreadVisibility() throws Exception {
        // init() writes 'available' on an async CompletableFuture thread while the
        // main server thread reads it in store()/retrieve()/isAvailable(). Without
        // volatile the JMM permits the main thread to never observe the write.
        Field field = MemoryManager.class.getDeclaredField("available");
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                "'available' is written from an async init thread and read from the "
                        + "main thread; it must be volatile");
    }

    @Test
    void formatTimeAgoBoundaries() {
        assertEquals("just now", MemoryManager.formatTimeAgo(ago(0)));
        assertEquals("just now", MemoryManager.formatTimeAgo(ago(59_000)));
        assertEquals("1m ago", MemoryManager.formatTimeAgo(ago(61_000)));
        assertEquals("59m ago", MemoryManager.formatTimeAgo(ago(59 * 60_000 + 1_000)));
        assertEquals("1h ago", MemoryManager.formatTimeAgo(ago(60 * 60_000 + 1_000)));
        assertEquals("23h ago", MemoryManager.formatTimeAgo(ago(23 * 60 * 60_000L + 60_000)));
        assertEquals("1d ago", MemoryManager.formatTimeAgo(ago(24 * 60 * 60_000L + 60_000)));
        assertEquals("3d ago", MemoryManager.formatTimeAgo(ago(3 * 24 * 60 * 60_000L + 60_000)));
    }

    @Test
    void firstNonNullPrefersNonEmptyFirstValue() {
        assertEquals("a", MemoryManager.firstNonNull("a", "b"));
        assertEquals("b", MemoryManager.firstNonNull(null, "b"));
        assertEquals("b", MemoryManager.firstNonNull("", "b"));
    }

    @Test
    void parseIntFallsBackOnMissingOrMalformed() {
        assertEquals(42, MemoryManager.parseInt("42", 7));
        assertEquals(7, MemoryManager.parseInt(null, 7));
        assertEquals(7, MemoryManager.parseInt("", 7));
        assertEquals(7, MemoryManager.parseInt("not-a-number", 7));
    }
}
