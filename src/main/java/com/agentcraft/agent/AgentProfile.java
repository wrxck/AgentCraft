package com.agentcraft.agent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentProfile {

    private final String id;
    private List<String> names;
    private List<String> skins;
    private String personality;
    private String specialty;
    private String fears = "";
    private String desires = "";
    private String temperament = "";
    private String quirks = "";
    private int maxTurns;
    private int maxSpawns;

    // Runtime: the actual name/skin assigned to a spawned agent
    private String name;
    private String skin;

    public AgentProfile(String id) {
        this.id = id;
    }

    /**
     * Create a runtime copy with a specific name/skin assigned.
     */
    public AgentProfile withAssignment(String assignedName, String assignedSkin) {
        AgentProfile copy = new AgentProfile(this.id);
        copy.names = this.names;
        copy.skins = this.skins;
        copy.personality = this.personality;
        copy.specialty = this.specialty;
        copy.fears = this.fears;
        copy.desires = this.desires;
        copy.temperament = this.temperament;
        copy.quirks = this.quirks;
        copy.maxTurns = this.maxTurns;
        copy.maxSpawns = this.maxSpawns;
        copy.name = assignedName;
        copy.skin = assignedSkin;
        return copy;
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
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                config = YamlConfiguration.loadConfiguration(reader);
            } catch (java.io.IOException e) {
                return null;
            }
        }

        // Support both single "name"/"skin" and list "names"/"skins"
        if (config.isList("names")) {
            profile.names = config.getStringList("names");
        } else {
            profile.names = new ArrayList<>();
            profile.names.add(config.getString("name", profileId));
        }

        if (config.isList("skins")) {
            profile.skins = config.getStringList("skins");
        } else {
            profile.skins = new ArrayList<>();
            profile.skins.add(config.getString("skin", "Steve"));
        }

        // Default name/skin for backward compat
        profile.name = profile.names.get(0);
        profile.skin = profile.skins.get(0);

        profile.personality = config.getString("personality", "A helpful Minecraft companion.");
        profile.specialty = config.getString("specialty", "general");
        profile.fears = config.getString("fears", "");
        profile.desires = config.getString("desires", "");
        profile.temperament = config.getString("temperament", "");
        profile.quirks = config.getString("quirks", "");
        profile.maxTurns = config.getInt("max-turns", 25);
        profile.maxSpawns = config.getInt("max-spawns", 2);

        return profile;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSkin() { return skin; }
    public List<String> getNames() { return Collections.unmodifiableList(names); }
    public List<String> getSkins() { return Collections.unmodifiableList(skins); }
    public String getPersonality() { return personality; }
    public String getSpecialty() { return specialty; }
    public String getFears() { return fears; }
    public String getDesires() { return desires; }
    public String getTemperament() { return temperament; }
    public String getQuirks() { return quirks; }
    public int getMaxTurns() { return maxTurns; }
    public int getMaxSpawns() { return maxSpawns; }
}
