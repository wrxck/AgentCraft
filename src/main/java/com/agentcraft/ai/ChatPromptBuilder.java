package com.agentcraft.ai;

import java.util.List;

public class ChatPromptBuilder {

    public static String buildUserMessage(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.playerName()).append("] ").append(msg.message()).append("\n");
        }
        return sb.toString().trim();
    }

    public record ChatMessage(String playerName, String message) {}
}
