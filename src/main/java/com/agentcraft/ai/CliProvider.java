package com.agentcraft.ai;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LLM provider that delegates to the Claude Code CLI via {@link ClaudeClient}.
 */
public class CliProvider implements LLMProvider {

    private final ClaudeClient client;

    public CliProvider(Plugin plugin) {
        this.client = new ClaudeClient(plugin);
    }

    @Override
    public CompletableFuture<Integer> streamChat(String prompt, String systemPrompt, File workingDir,
                                                  String sessionId, String model, Consumer<String> lineCallback) {
        return client.streamChat(prompt, systemPrompt, workingDir, sessionId, model, lineCallback);
    }

    @Override
    public void cancel() {
        client.cancel();
    }
}
