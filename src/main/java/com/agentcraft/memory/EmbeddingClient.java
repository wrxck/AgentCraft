package com.agentcraft.memory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * REST client for the embedding service (sentence-transformers).
 */
public class EmbeddingClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final Logger logger;

    public EmbeddingClient(String host, int port, Logger logger) {
        this.baseUrl = "http://" + host + ":" + port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.logger = logger;
    }

    /**
     * Get embedding vector for a single text. Runs async.
     */
    public CompletableFuture<float[]> embed(String text) {
        JsonObject body = new JsonObject();
        JsonArray texts = new JsonArray();
        texts.add(text);
        body.add("texts", texts);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .timeout(Duration.ofSeconds(15))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("[Memory] Embedding service returned " + response.statusCode());
                        return null;
                    }
                    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
                    JsonArray embeddings = result.getAsJsonArray("embeddings");
                    if (embeddings.isEmpty()) return null;

                    JsonArray vec = embeddings.get(0).getAsJsonArray();
                    float[] vector = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) {
                        vector[i] = vec.get(i).getAsFloat();
                    }
                    return vector;
                })
                .exceptionally(e -> {
                    logger.warning("[Memory] Embedding request failed: " + e.getMessage());
                    return null;
                });
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
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
