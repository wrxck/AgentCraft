package com.agentcraft;

import com.agentcraft.agent.AgentManager;
import com.agentcraft.ai.AIIntegration;
import com.agentcraft.ai.ConversationManager;
import com.agentcraft.memory.MemoryManager;
import com.agentcraft.command.AgentCommand;
import com.agentcraft.listener.ChatForwarder;
import com.agentcraft.listener.ChatListener;
import com.agentcraft.listener.ChunkListener;
import com.agentcraft.listener.PlayerJoinListener;
import com.agentcraft.listener.PlayerQuitListener;
import com.agentcraft.npc.NPCInteractListener;
import com.agentcraft.npc.NPCTracker;
import com.agentcraft.npc.SkinFetcher;
import com.agentcraft.util.MessageUtil;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("profiles/fullstack.yml", false);
        saveResource("profiles/frontend.yml", false);

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
                MessageUtil.send(player, MessageUtil.info("Use " + MessageUtil.highlight("/agent task " + npc.getName() + " <task>") + " to assign a coding task."));
            }
        });
        npcInteractListener.register();

        // Agent manager
        AgentManager.getInstance().init(this, npcTracker, skinFetcher);

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

        // Conversation system for NPC chat
        conversationManager = new ConversationManager(this);
        List<String> chatPlayers = getConfig().getStringList("chat-players");
        ChatForwarder chatForwarder = new ChatForwarder(chatPlayers, conversationManager);

        // Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(npcTracker), this);
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
        getLogger().info("AgentCraft disabled.");
    }

    public NPCTracker getNpcTracker() { return npcTracker; }
    public SkinFetcher getSkinFetcher() { return skinFetcher; }
    public AIIntegration getAiIntegration() { return aiIntegration; }
    public ConversationManager getConversationManager() { return conversationManager; }
    public MemoryManager getMemoryManager() { return memoryManager; }
}
