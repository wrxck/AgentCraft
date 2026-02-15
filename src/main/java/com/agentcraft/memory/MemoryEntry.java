package com.agentcraft.memory;

public record MemoryEntry(
        String id,
        String agentName,
        String playerName,
        String summary,
        long timestamp,
        float score
) {}
