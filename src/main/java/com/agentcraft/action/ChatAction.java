package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        Location npcLocation = agent.getNpc().getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Same-world guard: distanceSquared throws on cross-world locations,
            // which would kill the whole broadcast.
            if (!player.getWorld().equals(npcLocation.getWorld())) continue;
            if (player.getLocation().distanceSquared(npcLocation) <= 50 * 50) {
                player.sendMessage(formatted);
            }
        }
        return ActionResult.SUCCESS;
    }
}
