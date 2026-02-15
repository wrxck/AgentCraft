package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LookAtAction extends Action {

    private final Location target;

    public LookAtAction(AIAgent agent, Location target) {
        super(agent);
        this.target = target;
    }

    @Override
    public ActionResult tick() {
        FakePlayer npc = agent.getNpc();
        for (Player player : Bukkit.getOnlinePlayers()) {
            npc.lookAt(player, target);
        }
        return ActionResult.SUCCESS;
    }
}
