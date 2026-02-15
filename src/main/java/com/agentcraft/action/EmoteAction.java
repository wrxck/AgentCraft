package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.npc.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class EmoteAction extends Action {

    public enum EmoteType {
        SWING_ARM,
        SNEAK,
        UNSNEAK
    }

    private final EmoteType type;

    public EmoteAction(AIAgent agent, EmoteType type) {
        super(agent);
        this.type = type;
    }

    @Override
    public ActionResult tick() {
        FakePlayer npc = agent.getNpc();
        for (Player player : Bukkit.getOnlinePlayers()) {
            switch (type) {
                case SWING_ARM -> npc.swingArm(player);
                case SNEAK -> npc.sneak(player, true);
                case UNSNEAK -> npc.sneak(player, false);
            }
        }
        return ActionResult.SUCCESS;
    }
}
