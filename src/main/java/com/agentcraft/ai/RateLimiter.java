package com.agentcraft.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class RateLimiter {

    private final int maxTokens;
    private final long refillIntervalMs;
    private final LongSupplier clock;
    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxTokens, int refillIntervalSeconds) {
        this(maxTokens, refillIntervalSeconds, System::currentTimeMillis);
    }

    // Package-private clock seam for tests.
    RateLimiter(int maxTokens, int refillIntervalSeconds, LongSupplier clock) {
        this.maxTokens = maxTokens;
        // Guard against non-positive config values (would divide by zero in refill)
        this.refillIntervalMs = Math.max(1, refillIntervalSeconds) * 1000L;
        this.clock = clock;
    }

    public boolean tryConsume(UUID playerId) {
        TokenBucket bucket = buckets.computeIfAbsent(playerId,
                k -> new TokenBucket(maxTokens, clock.getAsLong()));
        return bucket.tryConsume();
    }

    private class TokenBucket {
        int tokens;
        long lastRefillTime;

        TokenBucket(int tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        void refill() {
            long now = clock.getAsLong();
            long elapsed = now - lastRefillTime;
            int newTokens = (int) (elapsed / refillIntervalMs);
            if (newTokens > 0) {
                tokens = Math.min(maxTokens, tokens + newTokens);
                // Advance by the whole intervals actually credited so fractional
                // progress toward the next token is never discarded.
                lastRefillTime += newTokens * refillIntervalMs;
            }
        }
    }
}
