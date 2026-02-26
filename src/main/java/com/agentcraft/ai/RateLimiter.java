package com.agentcraft.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

    private final int maxTokens;
    private final long refillIntervalMs;
    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxTokens, int refillIntervalSeconds) {
        this.maxTokens = maxTokens;
        this.refillIntervalMs = refillIntervalSeconds * 1000L;
    }

    public boolean tryConsume(UUID playerId) {
        TokenBucket bucket = buckets.computeIfAbsent(playerId,
                k -> new TokenBucket(maxTokens, System.currentTimeMillis()));
        return bucket.tryConsume();
    }

    public int remainingTokens(UUID playerId) {
        TokenBucket bucket = buckets.get(playerId);
        if (bucket == null) return maxTokens;
        return bucket.remainingTokens();
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

        synchronized int remainingTokens() {
            refill();
            return tokens;
        }

        synchronized void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            int newTokens = (int) (elapsed / refillIntervalMs);
            if (newTokens > 0) {
                tokens = Math.min(maxTokens, tokens + newTokens);
                lastRefillTime += newTokens * refillIntervalMs;
            }
        }
    }
}
