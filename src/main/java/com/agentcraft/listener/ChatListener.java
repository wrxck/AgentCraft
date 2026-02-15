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

        for (AIAgent agent : AgentManager.getInstance().getAllAgents()) {
            String agentName = agent.getNpc().getName().toLowerCase();

            if (!message.toLowerCase().contains(agentName)) continue;
            if (!agent.getNpc().getLocation().getWorld().equals(player.getWorld())) continue;
            if (agent.getNpc().getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;

            // Extract the task (everything after the agent name)
            String lowerMsg = message.toLowerCase();
            int nameIdx = lowerMsg.indexOf(agentName);
            String task = message.substring(nameIdx + agentName.length()).trim();

            // Strip leading punctuation
            if (!task.isEmpty() && (task.charAt(0) == ',' || task.charAt(0) == ':')) {
                task = task.substring(1).trim();
            }

            if (task.isEmpty()) continue;

            final String finalTask = task;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getAiIntegration().submitTask(agent, player, finalTask);
                MessageUtil.send(player, MessageUtil.info("Task sent to " + MessageUtil.highlight(agent.getNpc().getName()) + "."));
            });

            break;
        }
    }
}
