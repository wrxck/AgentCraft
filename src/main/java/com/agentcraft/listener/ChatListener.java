package com.agentcraft.listener;

import com.agentcraft.AgentCraftPlugin;
import com.agentcraft.agent.AIAgent;
import com.agentcraft.agent.AgentManager;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListener implements Listener {

    private final AgentCraftPlugin plugin;

    public ChatListener(AgentCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        double radius = plugin.getConfig().getDouble("chat-radius", 15);

        if (!player.hasPermission("agentcraft.use")) return;

        Map<String, AIAgent> agentsByName = new HashMap<>();
        List<String> agentNames = new ArrayList<>();
        for (AIAgent agent : AgentManager.getInstance().getAllAgents()) {
            String name = agent.getNpc().getName();
            agentNames.add(name);
            agentsByName.put(name, agent);
        }

        for (AgentMentionParser.Mention mention : AgentMentionParser.parseAll(message, agentNames)) {
            AIAgent agent = agentsByName.get(mention.agentName());
            if (agent == null) continue;
            if (mention.task().isEmpty()) continue;
            if (!agent.getNpc().getLocation().getWorld().equals(player.getWorld())) continue;
            if (agent.getNpc().getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;

            final String finalTask = mention.task();
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getAiIntegration().submitTask(agent, player, finalTask);
                MessageUtil.send(player, MessageUtil.info("Task sent to " + MessageUtil.highlight(agent.getNpc().getName()) + "."));
            });

            break;
        }
    }
}
