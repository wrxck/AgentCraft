package com.agentcraft.agent;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.npc.NPCTracker;
import com.agentcraft.npc.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentManager {

    private static AgentManager instance;

    private AgentCraftPlugin plugin;
    private NPCTracker tracker;
    private SkinFetcher skinFetcher;
    private final Map<String, AIAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, AgentProfile> profiles = new HashMap<>();

    private AgentManager() {}

    public static AgentManager getInstance() {
        if (instance == null) {
            instance = new AgentManager();
        }
        return instance;
    }

    public void init(AgentCraftPlugin plugin, NPCTracker tracker, SkinFetcher skinFetcher) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.skinFetcher = skinFetcher;
        loadProfiles();
    }

    public void loadProfiles() {
        profiles.clear();
        for (String profileId : List.of("fullstack", "frontend")) {
            AgentProfile profile = AgentProfile.load(plugin, profileId);
            if (profile != null) {
                profiles.put(profileId, profile);
                plugin.getLogger().info("Loaded profile: " + profileId + " (" + profile.getName() + ")");
            }
        }

        // Also load custom profiles from profiles/ folder
        File profileDir = new File(plugin.getDataFolder(), "profiles");
        if (profileDir.exists()) {
            File[] files = profileDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String id = file.getName().replace(".yml", "");
                    if (!profiles.containsKey(id)) {
                        AgentProfile profile = AgentProfile.load(plugin, id);
                        if (profile != null) {
                            profiles.put(id, profile);
                            plugin.getLogger().info("Loaded custom profile: " + id);
                        }
                    }
                }
            }
        }
    }

    public AIAgent spawnAgent(String profileId, Location location, Player spawner) {
        AgentProfile profile = profiles.get(profileId);
        if (profile == null) return null;

        String agentName = profile.getName();
        if (agents.containsKey(agentName.toLowerCase())) return null;

        // Create working directory for the agent
        String outputBase = plugin.getConfig().getString("agent-output-base", "/minecraft/agent_output");
        File workDir = new File(outputBase, agentName.toLowerCase());
        workDir.mkdirs();

        // Copy Claude credentials and set ownership for claude user
        setupAgentCredentials(workDir);
        chownForClaude(workDir);

        FakePlayer npc = new FakePlayer(plugin, agentName, location);
        tracker.register(npc);

        AIAgent agent = new AIAgent(plugin, npc, profile, workDir);
        agents.put(agentName.toLowerCase(), agent);

        // Fetch skin async, then spawn for all online players
        skinFetcher.fetch(profile.getSkin()).thenAccept(skinData -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                npc.setSkinData(skinData);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    npc.spawn(player);
                }
                agent.startBehavior();
            });
        });

        return agent;
    }

    private void setupAgentCredentials(File workDir) {
        String credentialsPath = plugin.getConfig().getString("claude-credentials-path",
                "/claude-auth/.credentials.json");
        File sourceCredentials = new File(credentialsPath);

        if (sourceCredentials.exists()) {
            File claudeDir = new File(workDir, ".claude");
            claudeDir.mkdirs();
            File destCredentials = new File(claudeDir, ".credentials.json");
            try {
                Files.copy(sourceCredentials.toPath(), destCredentials.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to copy Claude credentials: " + e.getMessage());
            }
        }
    }

    private void chownForClaude(File dir) {
        try {
            new ProcessBuilder("chown", "-R", "claude:claude", dir.getAbsolutePath())
                    .start().waitFor();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to chown agent dir: " + e.getMessage());
        }
    }

    public boolean despawnAgent(String name) {
        AIAgent agent = agents.remove(name.toLowerCase());
        if (agent == null) return false;

        agent.getBehaviorController().stop();
        agent.stop();
        FakePlayer npc = agent.getNpc();
        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.despawn(player);
        }
        tracker.unregister(npc);

        // Clear conversation session to prevent stale busy=true on respawn
        if (plugin.getConversationManager() != null) {
            plugin.getConversationManager().clearSession(npc.getName());
        }

        return true;
    }

    public AIAgent getAgent(String name) {
        return agents.get(name.toLowerCase());
    }

    public AIAgent getAgentByEntityId(int entityId) {
        for (AIAgent agent : agents.values()) {
            if (agent.getNpc().getEntityId() == entityId) {
                return agent;
            }
        }
        return null;
    }

    public Collection<AIAgent> getAllAgents() {
        return agents.values();
    }

    public Collection<String> getProfileIds() {
        return profiles.keySet();
    }

    public Collection<String> getAgentNames() {
        return agents.values().stream().map(a -> a.getNpc().getName()).toList();
    }

    public String getAgentOutputHost() {
        return plugin.getConfig().getString("agent-output-host", "/home/matt/minecraft/agent_output");
    }

    public NPCTracker getTracker() { return tracker; }

    public void shutdown() {
        for (AIAgent agent : agents.values()) {
            agent.getBehaviorController().stop();
            agent.stop();
        }
        tracker.despawnAll();
        agents.clear();
    }
}
