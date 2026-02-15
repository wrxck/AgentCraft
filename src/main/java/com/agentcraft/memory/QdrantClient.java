package com.agentcraft.memory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * REST client for Qdrant vector database.
 */
public class QdrantClient {

    private static final String COLLECTION_NAME = "npc_memories";
    private static final int VECTOR_DIM = 384; // all-MiniLM-L6-v2

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final Logger logger;

    public QdrantClient(String host, int port, String apiKey, Logger logger) {
        this.baseUrl = "http://" + host + ":" + port;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.logger = logger;
    }

    /**
     * Ensure the collection exists. Call once on startup.
     */
    public CompletableFuture<Boolean> ensureCollection() {
        // Check if collection exists
        HttpRequest check = buildRequest("/collections/" + COLLECTION_NAME, "GET", null);

        return httpClient.sendAsync(check, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        logger.info("[Memory] Qdrant collection '" + COLLECTION_NAME + "' exists");
                        return CompletableFuture.completedFuture(true);
                    }
                    // Create collection
                    return createCollection();
                })
                .exceptionally(e -> {
                    logger.warning("[Memory] Failed to check/create Qdrant collection: " + e.getMessage());
                    return false;
                });
    }

    private CompletableFuture<Boolean> createCollection() {
        JsonObject config = new JsonObject();
        JsonObject vectors = new JsonObject();
        vectors.addProperty("size", VECTOR_DIM);
        vectors.addProperty("distance", "Cosine");
        config.add("vectors", vectors);

        HttpRequest request = buildRequest("/collections/" + COLLECTION_NAME, "PUT",
                gson.toJson(config));

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        logger.info("[Memory] Created Qdrant collection '" + COLLECTION_NAME + "'");
                        return true;
                    }
                    logger.warning("[Memory] Failed to create collection: " + response.body());
                    return false;
                });
    }

    /**
     * Store a memory with its embedding vector.
     */
    public CompletableFuture<Boolean> upsert(String agentName, String playerName,
                                              String summary, float[] vector) {
        String id = UUID.randomUUID().toString();

        JsonObject point = new JsonObject();
        point.addProperty("id", id);

        JsonArray vectorArr = new JsonArray();
        for (float v : vector) vectorArr.add(v);
        point.add("vector", vectorArr);

        JsonObject payload = new JsonObject();
        payload.addProperty("agent_name", agentName);
        payload.addProperty("player_name", playerName);
        payload.addProperty("summary", summary);
        payload.addProperty("timestamp", System.currentTimeMillis());
        point.add("payload", payload);

        JsonObject body = new JsonObject();
        JsonArray points = new JsonArray();
        points.add(point);
        body.add("points", points);

        HttpRequest request = buildRequest(
                "/collections/" + COLLECTION_NAME + "/points", "PUT",
                gson.toJson(body));

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) return true;
                    logger.warning("[Memory] Qdrant upsert failed: " + response.body());
                    return false;
                })
                .exceptionally(e -> {
                    logger.warning("[Memory] Qdrant upsert error: " + e.getMessage());
                    return false;
                });
    }

    /**
     * Search for similar memories by vector. Returns top N results filtered by agent name.
     */
    public CompletableFuture<List<MemoryEntry>> search(String agentName, float[] queryVector, int limit) {
        JsonObject body = new JsonObject();

        JsonArray vectorArr = new JsonArray();
        for (float v : queryVector) vectorArr.add(v);
        body.add("vector", vectorArr);

        body.addProperty("limit", limit);
        body.addProperty("with_payload", true);

        // Filter by agent name
        JsonObject filter = new JsonObject();
        JsonArray must = new JsonArray();
        JsonObject agentFilter = new JsonObject();
        JsonObject match = new JsonObject();
        match.addProperty("value", agentName.toLowerCase());
        agentFilter.add("match", match);
        agentFilter.addProperty("key", "agent_name");
        must.add(agentFilter);
        filter.add("must", must);
        body.add("filter", filter);

        HttpRequest request = buildRequest(
                "/collections/" + COLLECTION_NAME + "/points/search", "POST",
                gson.toJson(body));

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("[Memory] Qdrant search failed: " + response.body());
                        return List.<MemoryEntry>of();
                    }
                    return parseSearchResults(response.body());
                })
                .exceptionally(e -> {
                    logger.warning("[Memory] Qdrant search error: " + e.getMessage());
                    return List.of();
                });
    }

    private List<MemoryEntry> parseSearchResults(String responseBody) {
        List<MemoryEntry> results = new ArrayList<>();
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray resultArr = json.getAsJsonArray("result");
            if (resultArr == null) return results;

            for (int i = 0; i < resultArr.size(); i++) {
                JsonObject hit = resultArr.get(i).getAsJsonObject();
                float score = hit.has("score") ? hit.get("score").getAsFloat() : 0;
                String id = hit.has("id") ? hit.get("id").getAsString() : "";

                JsonObject payload = hit.has("payload") ? hit.getAsJsonObject("payload") : new JsonObject();
                String agent = payload.has("agent_name") ? payload.get("agent_name").getAsString() : "";
                String player = payload.has("player_name") ? payload.get("player_name").getAsString() : "";
                String summary = payload.has("summary") ? payload.get("summary").getAsString() : "";
                long timestamp = payload.has("timestamp") ? payload.get("timestamp").getAsLong() : 0;

                results.add(new MemoryEntry(id, agent, player, summary, timestamp, score));
            }
        } catch (Exception e) {
            logger.warning("[Memory] Failed to parse search results: " + e.getMessage());
        }
        return results;
    }

    private HttpRequest buildRequest(String path, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("api-key", apiKey);
        }

        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/healthz"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
