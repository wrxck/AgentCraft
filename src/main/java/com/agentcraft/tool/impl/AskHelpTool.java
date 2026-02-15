package com.agentcraft.tool.impl;

import com.agentcraft.agent.AIAgent;
import com.agentcraft.tool.MinecraftTool;
import com.agentcraft.tool.ToolResult;
import com.agentcraft.util.MessageUtil;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AskHelpTool implements MinecraftTool {

    @Override public String getName() { return "ask_help"; }

    @Override public String getDescription() {
        return "Ask nearby players for help with a task you're stuck on.";
    }

    @Override public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("message", "string: what you need help with");
        return schema;
    }

    @Override public ToolResult execute(AIAgent agent, JsonObject params) {
        String message = params.has("message") ? params.get("message").getAsString() : null;
        if (message == null || message.isEmpty()) return ToolResult.fail("No message specified");

        String agentName = agent.getNpc().getName();
        String formatted = MessageUtil.agentChat(agentName, "[Needs help] " + message);

        List<String> recipients = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatted);
            recipients.add(player.getName());
        }

        if (recipients.isEmpty()) {
            return ToolResult.ok("Help request sent, but no players are online");
        }

        return ToolResult.ok("Asked for help. Online players: " + String.join(", ", recipients));
    }
}
