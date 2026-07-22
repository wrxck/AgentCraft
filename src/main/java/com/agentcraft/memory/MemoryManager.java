package com.agentcraft.memory;

import com.agentcraft.util.EnvFileParser;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages NPC long-term memory using vector embeddings stored in Qdrant.
 * Memories are conversation summaries that can be retrieved by semantic similarity.
 */
public class MemoryManager {

    private static final int MAX_MEMORIES_PER_QUERY = 5;
    private static final float MIN_RELEVANCE_SCORE = 0.3f;
    private static final String SECRETS_PATH = "/minecraft/fleet-secrets.env";

    private final Plugin plugin;
    private final Logger logger;
    private QdrantClient qdrant;
    private EmbeddingClient embeddings;
    // Written from the async init thread (CompletableFuture.runAsync) and read
    // from the main server thread; volatile guarantees the write is visible.
    private volatile boolean available;

    public MemoryManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Initialize connections to Qdrant and embedding service.
     * Reads config from plugin config, with fleet secrets for API key.
     */
    public void init() {
        Map<String, String> secrets = loadSecrets();

        String qdrantHost = firstNonNull(
                secrets.get("QDRANT_HOST"),
                plugin.getConfig().getString("memory.qdrant-host", "qdrant"));
        int qdrantPort = parseInt(
                secrets.get("QDRANT_PORT"),
                plugin.getConfig().getInt("memory.qdrant-port", 6333));
        String apiKey = firstNonNull(
                secrets.get("QDRANT_API_KEY"),
                plugin.getConfig().getString("memory.qdrant-api-key", ""));

        String embHost = firstNonNull(
                secrets.get("EMBEDDINGS_HOST"),
                plugin.getConfig().getString("memory.embeddings-host", "embeddings"));
        int embPort = parseInt(
                secrets.get("EMBEDDINGS_PORT"),
                plugin.getConfig().getInt("memory.embeddings-port", 8090));

        qdrant = new QdrantClient(qdrantHost, qdrantPort, apiKey, logger);
        embeddings = new EmbeddingClient(embHost, embPort, logger);

        // Check services and create collection
        CompletableFuture.runAsync(() -> {
            if (!embeddings.isHealthy()) {
                logger.warning("[Memory] Embedding service not available — memory disabled");
                available = false;
                return;
            }
            if (!qdrant.isHealthy()) {
                logger.warning("[Memory] Qdrant not available — memory disabled");
                available = false;
                return;
            }
            qdrant.ensureCollection().thenAccept(ok -> {
                available = ok;
                if (ok) {
                    logger.info("[Memory] Memory system initialized (Qdrant + embeddings)");
                }
            });
        });
    }

    /**
     * Store a conversation memory. Call after NPC responds.
     * @param agentName NPC name
     * @param playerName Player who was chatting
     * @param conversationSummary Summary of the conversation exchange
     */
    public void store(String agentName, String playerName, String conversationSummary) {
        if (!available || conversationSummary.isBlank()) return;

        embeddings.embed(conversationSummary).thenCompose(vector -> {
            if (vector == null) return CompletableFuture.completedFuture(false);
            return qdrant.upsert(agentName.toLowerCase(), playerName, conversationSummary, vector);
        }).thenAccept(ok -> {
            if (ok) {
                logger.info("[Memory] Stored memory for " + agentName + " (chat with " + playerName + ")");
            }
        });
    }

    /**
     * Retrieve relevant memories for an NPC given a conversation context.
     * Returns a formatted string of memories to include in the system prompt.
     */
    public CompletableFuture<String> retrieve(String agentName, String currentContext) {
        if (!available || currentContext.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        return embeddings.embed(currentContext).thenCompose(vector -> {
            if (vector == null) return CompletableFuture.completedFuture(List.<MemoryEntry>of());
            return qdrant.search(agentName.toLowerCase(), vector, MAX_MEMORIES_PER_QUERY);
        }).thenApply(memories -> {
            if (memories.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("[MEMORIES]\n");
            sb.append("You remember these past interactions:\n");

            int count = 0;
            for (MemoryEntry memory : memories) {
                if (memory.score() < MIN_RELEVANCE_SCORE) continue;
                count++;
                String timeAgo = formatTimeAgo(memory.timestamp());
                sb.append("- (").append(timeAgo).append(") ").append(memory.summary()).append("\n");
            }

            return count > 0 ? sb.toString() : "";
        }).exceptionally(e -> {
            logger.warning("[Memory] Retrieve failed: " + e.getMessage());
            return "";
        });
    }

    public boolean isAvailable() {
        return available;
    }

    private Map<String, String> loadSecrets() {
        try (BufferedReader reader = new BufferedReader(new FileReader(SECRETS_PATH))) {
            // Shared parser (trim, comments, quote-stripping) so the same
            // secrets file parses identically here and in PersistenceManager.
            return EnvFileParser.parse(reader);
        } catch (Exception e) {
            logger.info("[Memory] No fleet secrets found at " + SECRETS_PATH + " — using config defaults");
            return new HashMap<>();
        }
    }

    static String formatTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    static String firstNonNull(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }

    static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }
}
