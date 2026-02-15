package com.agentcraft;

import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.AIIntegration;
import com.agentcraft.ai.AnthropicProvider;
import com.agentcraft.ai.CliProvider;
import com.agentcraft.ai.ConversationManager;
import com.agentcraft.ai.LLMProvider;
import com.agentcraft.behavior.ChunkForceManager;
import com.agentcraft.memory.MemoryManager;
import com.agentcraft.persistence.PersistenceManager;
import com.agentcraft.command.AgentCommand;
import com.agentcraft.listener.ChatForwarder;
import com.agentcraft.listener.ChatListener;
import com.agentcraft.listener.ChunkListener;
import com.agentcraft.listener.PlayerJoinListener;
import com.agentcraft.listener.PlayerQuitListener;
import com.agentcraft.npc.NPCInteractListener;
import com.agentcraft.npc.NPCTracker;
import com.agentcraft.npc.SkinFetcher;
import com.agentcraft.tool.ToolRegistry;
import com.agentcraft.tool.impl.*;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class AgentCraftPlugin extends JavaPlugin {

    private NPCTracker npcTracker;
    private SkinFetcher skinFetcher;
    private NPCInteractListener npcInteractListener;
    private AIIntegration aiIntegration;
    private ConversationManager conversationManager;
    private MemoryManager memoryManager;
    private ToolRegistry toolRegistry;
    private PersistenceManager persistenceManager;
    private ChunkForceManager chunkForceManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("profiles/hermit.yml", false);
        saveResource("profiles/trickster.yml", false);
        saveResource("profiles/builder.yml", false);
        saveResource("profiles/miner.yml", false);
        saveResource("profiles/farmer.yml", false);
        saveResource("profiles/guard.yml", false);
        saveResource("profiles/explorer.yml", false);
        saveResource("profiles/merchant.yml", false);

        // Create agent output base directory
        String outputBase = getConfig().getString("agent-output-base", "/minecraft/agent_output");
        new File(outputBase).mkdirs();

        // NPC system
        npcTracker = new NPCTracker();
        skinFetcher = new SkinFetcher(this);

        // NPC interaction via ProtocolLib
        npcInteractListener = new NPCInteractListener(this, npcTracker);
        npcInteractListener.setInteractHandler((player, npc) -> {
            AgentManager mgr = AgentManager.getInstance();
            var agent = mgr.getAgentByEntityId(npc.getEntityId());
            if (agent != null) {
                MessageUtil.send(player, MessageUtil.info("This is " + MessageUtil.highlight(npc.getName())
                        + " (" + agent.getProfile().getSpecialty() + "). State: " + agent.getState()));
                MessageUtil.send(player, MessageUtil.info("Use " + MessageUtil.highlight("/agent task " + npc.getName() + " <task>") + " to assign a task."));
            }
        });
        npcInteractListener.register();

        // Agent manager
        AgentManager.getInstance().init(this, npcTracker, skinFetcher);

        // Chunk force-loading for offline NPC activity
        boolean chunkForceEnabled = getConfig().getBoolean("chunk-forcing.enabled", true);
        int chunkForceRadius = getConfig().getInt("chunk-forcing.radius", 1);
        chunkForceManager = new ChunkForceManager(getLogger(), chunkForceEnabled, chunkForceRadius);
        AgentManager.getInstance().setChunkForceManager(chunkForceManager);

        // Persistence (PostgreSQL via fleet secrets)
        persistenceManager = new PersistenceManager(this);
        if (persistenceManager.init()) {
            AgentManager.getInstance().setPersistence(persistenceManager);
            // Restore agents after worlds are fully loaded (1-tick delay)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                AgentManager.getInstance().restoreAgents();
            }, 1L);
            // Auto-save every 5 minutes (6000 ticks) — must run on main thread for Bukkit API access
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                AgentManager.getInstance().saveAll();
            }, 6000L, 6000L);
        }

        // AI integration
        aiIntegration = new AIIntegration(this);

        // Memory system (vector DB)
        memoryManager = new MemoryManager(this);
        memoryManager.init();

        // Commands
        AgentCommand agentCommand = new AgentCommand(this);
        PluginCommand cmd = getCommand("agent");
        if (cmd != null) {
            cmd.setExecutor(agentCommand);
            cmd.setTabCompleter(agentCommand);
        }

        // Tool system
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new GotoTool());
        toolRegistry.register(new FollowTool());
        toolRegistry.register(new FleeTool());
        toolRegistry.register(new AttackTool());
        toolRegistry.register(new MineTool());
        toolRegistry.register(new GatherTool());
        toolRegistry.register(new PlaceTool());
        toolRegistry.register(new LookAtTool());
        toolRegistry.register(new ApproachTool());
        toolRegistry.register(new WanderTool());
        toolRegistry.register(new GoHomeTool());
        toolRegistry.register(new IdleTool());
        toolRegistry.register(new EatTool());
        toolRegistry.register(new DropTool());
        toolRegistry.register(new ExpeditionTool());
        toolRegistry.register(new ScanAreaTool());
        // Primitive tools for multi-turn chaining
        toolRegistry.register(new GetPositionTool());
        toolRegistry.register(new CheckInventoryTool());
        toolRegistry.register(new CheckBlockTool());
        toolRegistry.register(new BreakBlockTool());
        toolRegistry.register(new PlaceBlockTool());
        toolRegistry.register(new CraftTool());
        toolRegistry.register(new InteractBlockTool());
        toolRegistry.register(new GiveItemTool());
        toolRegistry.register(new SetSignTool());
        toolRegistry.register(new BuildStructureTool());
        toolRegistry.register(new StoreItemsTool());
        toolRegistry.register(new EmoteTool());
        toolRegistry.register(new EquipTool());
        toolRegistry.register(new SmeltTool());
        toolRegistry.register(new FarmTool());
        toolRegistry.register(new BreedTool());
        toolRegistry.register(new SleepTool());
        toolRegistry.register(new AskHelpTool());

        // LLM provider selection for NPC chat
        String providerType = getConfig().getString("llm-provider", "cli");
        LLMProvider llmProvider;
        if ("api".equals(providerType)) {
            String anthropicKey = getConfig().getString("anthropic-api-key", "");
            if (anthropicKey.isEmpty()) {
                getLogger().severe("llm-provider=api but no anthropic-api-key configured! Falling back to CLI.");
                llmProvider = new CliProvider(this);
            } else {
                llmProvider = new AnthropicProvider(anthropicKey, getLogger());
                getLogger().info("Using Anthropic HTTP API for NPC chat");
            }
        } else {
            llmProvider = new CliProvider(this);
            getLogger().info("Using Claude CLI for NPC chat");
        }

        // Conversation system for NPC chat
        conversationManager = new ConversationManager(this, llmProvider);
        List<String> chatPlayers = getConfig().getStringList("chat-players");
        ChatForwarder chatForwarder = new ChatForwarder(chatPlayers, conversationManager);

        // Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(npcTracker, this), this);
        pm.registerEvents(new PlayerQuitListener(npcTracker), this);
        pm.registerEvents(new ChunkListener(npcTracker), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(chatForwarder, this);

        getLogger().info("AgentCraft enabled!");
    }

    @Override
    public void onDisable() {
        if (conversationManager != null) conversationManager.shutdown();
        if (aiIntegration != null) aiIntegration.shutdown();
        AgentManager.getInstance().shutdown();
        if (persistenceManager != null) persistenceManager.shutdown();
        getLogger().info("AgentCraft disabled.");
    }

    public NPCTracker getNpcTracker() { return npcTracker; }
    public SkinFetcher getSkinFetcher() { return skinFetcher; }
    public AIIntegration getAiIntegration() { return aiIntegration; }
    public ConversationManager getConversationManager() { return conversationManager; }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
}
