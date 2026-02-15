package com.agentcraft.agent;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.npc.AgentEquipment;
import com.agentcraft.npc.FakePlayer;
import com.agentcraft.npc.NPCTracker;
import com.agentcraft.npc.SkinFetcher;
import com.agentcraft.persistence.PersistenceManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import com.agentcraft.behavior.ChunkForceManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class AgentManager {

    private static AgentManager instance;

    private AgentCraftPlugin plugin;
    private NPCTracker tracker;
    private SkinFetcher skinFetcher;
    private PersistenceManager persistence;
    private ChunkForceManager chunkForceManager;
    private final Map<String, AIAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, AgentProfile> profiles = new ConcurrentHashMap<>();

    // Track used names and skins globally to prevent duplicates
    private final Set<String> usedNames = new HashSet<>();
    private final Set<String> usedSkins = new HashSet<>();

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

    public void setPersistence(PersistenceManager persistence) {
        this.persistence = persistence;
    }

    public void setChunkForceManager(ChunkForceManager chunkForceManager) {
        this.chunkForceManager = chunkForceManager;
    }

    public void loadProfiles() {
        profiles.clear();
        for (String profileId : List.of("hermit", "trickster", "builder", "miner", "farmer", "guard", "explorer", "merchant")) {
            AgentProfile profile = AgentProfile.load(plugin, profileId);
            if (profile != null) {
                profiles.put(profileId, profile);
                plugin.getLogger().info("Loaded profile: " + profileId
                        + " (" + profile.getNames().size() + " names, "
                        + profile.getSkins().size() + " skins, max "
                        + profile.getMaxSpawns() + ")");
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

    /**
     * Restore all agents from the database after server restart.
     * Called after init() and after all worlds are loaded.
     */
    public void restoreAgents() {
        if (persistence == null || !persistence.isAvailable()) {
            plugin.getLogger().info("[Persistence] No database available, skipping agent restore");
            return;
        }

        List<PersistenceManager.AgentData> savedAgents = persistence.loadAllAgents();
        if (savedAgents.isEmpty()) {
            plugin.getLogger().info("[Persistence] No saved agents to restore");
            return;
        }

        plugin.getLogger().info("[Persistence] Restoring " + savedAgents.size() + " agents...");

        for (PersistenceManager.AgentData data : savedAgents) {
            try {
                restoreAgent(data);
            } catch (Exception e) {
                plugin.getLogger().warning("[Persistence] Failed to restore agent "
                        + data.name() + ": " + e.getMessage());
            }
        }
    }

    private void restoreAgent(PersistenceManager.AgentData data) {
        // Validate profile exists
        AgentProfile baseProfile = profiles.get(data.profileId());
        if (baseProfile == null) {
            plugin.getLogger().warning("[Persistence] Unknown profile " + data.profileId()
                    + " for agent " + data.name() + ", skipping");
            return;
        }

        // Validate world exists
        World world = Bukkit.getWorld(data.world());
        if (world == null) {
            plugin.getLogger().warning("[Persistence] World " + data.world()
                    + " not loaded for agent " + data.name() + ", skipping");
            return;
        }

        // Skip if name already in use
        if (agents.containsKey(data.name().toLowerCase())) {
            plugin.getLogger().info("[Persistence] Agent " + data.name() + " already exists, skipping");
            return;
        }

        Location location = new Location(world, data.x(), data.y(), data.z(), data.yaw(), 0);
        String agentName = data.name();
        String skinName = data.skin();

        // Create a runtime profile with the saved name/skin
        AgentProfile assignedProfile = baseProfile.withAssignment(agentName, skinName);

        // Set up working directory
        String outputBase = plugin.getConfig().getString("agent-output-base", "/minecraft/agent_output");
        File workDir = new File(outputBase, agentName.toLowerCase());
        workDir.mkdirs();
        setupAgentCredentials(workDir);
        chownForClaude(workDir);

        // Create NPC and agent
        FakePlayer npc = new FakePlayer(plugin, agentName, location);
        npc.setEquipment(new AgentEquipment());
        tracker.register(npc);

        AIAgent agent = new AIAgent(plugin, npc, assignedProfile, workDir);
        agents.put(agentName.toLowerCase(), agent);
        usedNames.add(agentName.toLowerCase());
        usedSkins.add(skinName.toLowerCase());

        // Restore inventory
        List<PersistenceManager.InventoryItem> savedInventory = persistence.loadInventory(agentName);
        for (PersistenceManager.InventoryItem item : savedInventory) {
            try {
                Material mat = Material.valueOf(item.material());
                agent.getBehaviorController().addToInventory(new ItemStack(mat, item.amount()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Persistence] Unknown material " + item.material()
                        + " in inventory for " + agentName);
            }
        }

        // Fetch skin and spawn
        skinFetcher.fetch(skinName).handle((skinData, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to fetch skin for " + agentName + ": " + throwable.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (skinData != null) {
                    npc.setSkinData(skinData);
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    npc.spawn(player);
                }
                agent.startBehavior();
                if (chunkForceManager != null) {
                    chunkForceManager.updateForced(agent);
                }
            });
            return null;
        });

        plugin.getLogger().info("[Persistence] Restored agent " + agentName
                + " at " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
    }

    public AIAgent spawnAgent(String profileId, Location location, Player spawner) {
        AgentProfile baseProfile = profiles.get(profileId);
        if (baseProfile == null) return null;

        // Check max-per-profile limit
        long activeCount = agents.values().stream()
                .filter(a -> a.getProfile().getId().equals(profileId))
                .count();
        if (activeCount >= baseProfile.getMaxSpawns()) {
            plugin.getLogger().info("Max agents reached for profile " + profileId
                    + " (" + activeCount + "/" + baseProfile.getMaxSpawns() + ")");
            return null;
        }

        // Pick an unused name from the pool
        String agentName = pickUnusedName(baseProfile);
        if (agentName == null) {
            plugin.getLogger().warning("No available names for profile " + profileId);
            return null;
        }

        // Pick an unused skin from the pool
        String skinName = pickUnusedSkin(baseProfile);
        if (skinName == null) {
            plugin.getLogger().warning("No available skins for profile " + profileId);
            return null;
        }

        // Create a runtime profile copy with the assigned name/skin
        AgentProfile assignedProfile = baseProfile.withAssignment(agentName, skinName);

        // Create working directory for the agent
        String outputBase = plugin.getConfig().getString("agent-output-base", "/minecraft/agent_output");
        File workDir = new File(outputBase, agentName.toLowerCase());
        workDir.mkdirs();

        // Copy Claude credentials and set ownership for claude user
        setupAgentCredentials(workDir);
        chownForClaude(workDir);

        FakePlayer npc = new FakePlayer(plugin, agentName, location);
        npc.setEquipment(new AgentEquipment());
        tracker.register(npc);

        AIAgent agent = new AIAgent(plugin, npc, assignedProfile, workDir);
        agents.put(agentName.toLowerCase(), agent);
        usedNames.add(agentName.toLowerCase());
        usedSkins.add(skinName.toLowerCase());

        // Fetch skin async, then spawn for all online players
        skinFetcher.fetch(skinName).handle((skinData, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to fetch skin for " + agentName + ": " + throwable.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (skinData != null) {
                    npc.setSkinData(skinData);
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    npc.spawn(player);
                }
                agent.startBehavior();
                if (chunkForceManager != null) {
                    chunkForceManager.updateForced(agent);
                }
            });
            return null;
        });

        // Persist new agent to database
        persistAgent(agent);

        return agent;
    }

    private String pickUnusedName(AgentProfile profile) {
        List<String> available = new ArrayList<>();
        for (String name : profile.getNames()) {
            if (!usedNames.contains(name.toLowerCase()) && !agents.containsKey(name.toLowerCase())) {
                available.add(name);
            }
        }
        if (available.isEmpty()) return null;
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private String pickUnusedSkin(AgentProfile profile) {
        List<String> available = new ArrayList<>();
        for (String skin : profile.getSkins()) {
            if (!usedSkins.contains(skin.toLowerCase())) {
                available.add(skin);
            }
        }
        if (available.isEmpty()) {
            // Fall back: allow skin reuse if pool exhausted (skins are cosmetic)
            List<String> allSkins = profile.getSkins();
            return allSkins.get(ThreadLocalRandom.current().nextInt(allSkins.size()));
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
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

        // Release forced chunks
        if (chunkForceManager != null) {
            chunkForceManager.releaseAll(name);
        }

        // Release name and skin back to pools
        usedNames.remove(name.toLowerCase());
        usedSkins.remove(agent.getProfile().getSkin().toLowerCase());

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

        // Remove from database
        if (persistence != null && persistence.isAvailable()) {
            persistence.deleteAgent(name);
        }

        return true;
    }

    /**
     * Persist a single agent to the database.
     */
    public void persistAgent(AIAgent agent) {
        if (persistence == null || !persistence.isAvailable()) return;

        Location loc = agent.getNpc().getLocation();
        PersistenceManager.AgentData data = new PersistenceManager.AgentData(
                agent.getNpc().getName(),
                agent.getProfile().getId(),
                agent.getProfile().getSkin(),
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                agent.getState().name(),
                agent.getCurrentTask()
        );
        persistence.saveAgent(data);

        // Save inventory
        List<PersistenceManager.InventoryItem> items = new ArrayList<>();
        for (ItemStack stack : agent.getBehaviorController().getInventory()) {
            items.add(new PersistenceManager.InventoryItem(stack.getType().name(), stack.getAmount()));
        }
        persistence.saveInventory(agent.getNpc().getName(), items);
    }

    /**
     * Save all agents to database. Called on shutdown and periodically.
     */
    public void saveAll() {
        if (persistence == null || !persistence.isAvailable()) return;

        for (AIAgent agent : agents.values()) {
            try {
                persistAgent(agent);
            } catch (Exception e) {
                plugin.getLogger().warning("[Persistence] Failed to save agent "
                        + agent.getNpc().getName() + ": " + e.getMessage());
            }
        }
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
        // Save all agents before stopping
        saveAll();

        for (AIAgent agent : agents.values()) {
            agent.getBehaviorController().stop();
            agent.stop();
        }
        if (chunkForceManager != null) {
            chunkForceManager.shutdown();
        }
        tracker.despawnAll();
        agents.clear();
        usedNames.clear();
        usedSkins.clear();
    }
}
