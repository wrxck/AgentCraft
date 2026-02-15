package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ChatAction extends Action {

    private final String message;

    public ChatAction(AIAgent agent, String message) {
        super(agent);
        this.message = message;
    }

    @Override
    public ActionResult tick() {
        String formatted = MessageUtil.agentChat(agent.getNpc().getName(), message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().distanceSquared(agent.getNpc().getLocation()) <= 50 * 50) {
                player.sendMessage(formatted);
            }
        }
        return ActionResult.SUCCESS;
    }
}
