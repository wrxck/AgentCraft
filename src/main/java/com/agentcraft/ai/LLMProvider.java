package com.agentcraft.ai;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstraction for LLM chat providers.
 * Implementations emit NDJSON lines via lineCallback compatible with {@link StreamEvent#parse(String)}.
 */
public interface LLMProvider {

    /**
     * Stream a chat request, emitting NDJSON lines to the callback.
     *
     * @param prompt       The user message
     * @param systemPrompt System prompt (may be null for follow-up turns)
     * @param workingDir   Working directory for the agent
     * @param sessionId    Session ID for multi-turn (null for new conversation)
     * @param model        Model shorthand (e.g. "haiku", "sonnet", "opus")
     * @param lineCallback Receives NDJSON lines compatible with StreamEvent.parse()
     * @return Future with exit code (0 = success)
     */
    CompletableFuture<Integer> streamChat(String prompt, String systemPrompt, File workingDir,
                                          String sessionId, String model, Consumer<String> lineCallback);

    void cancel();
}
