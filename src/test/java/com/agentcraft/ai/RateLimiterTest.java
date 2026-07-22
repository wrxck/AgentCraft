package com.agentcraft.ai;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final int MAX_TOKENS = 2;
    private static final int REFILL_SECONDS = 10;

    @Test
    void fractionalIntervalProgressIsNotDiscarded() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(MAX_TOKENS, REFILL_SECONDS, now::get);
        UUID player = UUID.randomUUID();

        // Drain the bucket at t=0
        assertTrue(limiter.tryConsume(player));
        assertTrue(limiter.tryConsume(player));
        assertFalse(limiter.tryConsume(player), "bucket should be empty");

        // Advance 1.9x the refill interval: exactly one token earned, consume it.
        now.set(19_000);
        assertTrue(limiter.tryConsume(player), "one token earned after 1.9 intervals");

        // Advance another 0.1x interval (2.0 intervals total since empty).
        // The 0.9 fractional progress must not have been discarded.
        now.set(20_000);
        assertTrue(limiter.tryConsume(player),
                "second token should be earned at exactly 2.0 intervals of elapsed time");
    }

    @Test
    void tokensDoNotExceedMax() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(MAX_TOKENS, REFILL_SECONDS, now::get);
        UUID player = UUID.randomUUID();

        // Long idle period: bucket must cap at MAX_TOKENS.
        now.set(1_000_000);
        assertTrue(limiter.tryConsume(player));
        assertTrue(limiter.tryConsume(player));
        assertFalse(limiter.tryConsume(player), "bucket must be capped at maxTokens");
    }

    @Test
    void zeroRefillIntervalDoesNotThrow() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, 0, now::get);
        UUID player = UUID.randomUUID();

        assertDoesNotThrow(() -> limiter.tryConsume(player),
                "refill interval of 0 must not cause division by zero");
        now.set(5_000);
        assertDoesNotThrow(() -> limiter.tryConsume(player));
    }
}
