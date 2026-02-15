package com.agentcraft.agent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AgentProfile {

    private final String id;
    private String name;
    private String skin;
    private String personality;
    private String specialty;
    private int maxTurns;

    public AgentProfile(String id) {
        this.id = id;
    }

    public static AgentProfile load(Plugin plugin, String profileId) {
        AgentProfile profile = new AgentProfile(profileId);

        // Try external file first, then bundled resource
        File file = new File(plugin.getDataFolder(), "profiles/" + profileId + ".yml");
        YamlConfiguration config;

        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            InputStream stream = plugin.getResource("profiles/" + profileId + ".yml");
            if (stream == null) return null;
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
        }

        profile.name = config.getString("name", profileId);
        profile.skin = config.getString("skin", "Steve");
        profile.personality = config.getString("personality", "A helpful coding agent.");
        profile.specialty = config.getString("specialty", "general development");
        profile.maxTurns = config.getInt("max-turns", 25);

        return profile;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSkin() { return skin; }
    public String getPersonality() { return personality; }
    public String getSpecialty() { return specialty; }
    public int getMaxTurns() { return maxTurns; }
}
