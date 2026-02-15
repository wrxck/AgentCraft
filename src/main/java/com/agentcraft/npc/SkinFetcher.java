package com.agentcraft.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SkinFetcher {

    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final Plugin plugin;
    private final ConcurrentHashMap<String, SkinData> cache = new ConcurrentHashMap<>();

    public SkinFetcher(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<SkinData> fetch(String username) {
        SkinData cached = cache.get(username.toLowerCase());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String uuid = resolveUUID(username);
                if (uuid == null) {
                    return SkinData.EMPTY;
                }

                SkinData skin = resolveTextures(uuid);
                cache.put(username.toLowerCase(), skin);
                return skin;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch skin for " + username, e);
                return SkinData.EMPTY;
            }
        });
    }

    private String resolveUUID(String username) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(UUID_URL + username).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.get("id").getAsString();
        }
    }

    private SkinData resolveTextures(String uuid) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(PROFILE_URL + uuid + "?unsigned=false").toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (conn.getResponseCode() != 200) {
            return SkinData.EMPTY;
        }

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray properties = json.getAsJsonArray("properties");

            for (JsonElement element : properties) {
                JsonObject prop = element.getAsJsonObject();
                if ("textures".equals(prop.get("name").getAsString())) {
                    String value = prop.get("value").getAsString();
                    String signature = prop.has("signature") ? prop.get("signature").getAsString() : "";
                    return new SkinData(value, signature);
                }
            }
        }

        return SkinData.EMPTY;
    }

    public SkinData getCached(String username) {
        return cache.getOrDefault(username.toLowerCase(), SkinData.EMPTY);
    }
}
