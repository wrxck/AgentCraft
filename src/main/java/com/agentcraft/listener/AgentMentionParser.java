package com.agentcraft.listener;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Shared, pure parser for agent mentions in chat messages, used by both
 * {@link ChatListener} (task dispatch) and {@link ChatForwarder} (conversation
 * forwarding) so the two can never diverge on what counts as a mention.
 *
 * A mention is an agent name appearing as a whole word (case-insensitive,
 * bounded by non-letter/digit characters), e.g. "Bob, come here" mentions
 * "Bob" but "bobby is my friend" does not. The task text is everything after
 * the name, with one leading ',' or ':' stripped; it may be empty for a
 * name-only message.
 */
public final class AgentMentionParser {

    /** A detected mention: which agent, and the (possibly empty) task text after the name. */
    public record Mention(String agentName, String task) {}

    private AgentMentionParser() {
    }

    /**
     * Returns the first mention found, checking agent names in iteration order.
     */
    public static Optional<Mention> parse(String message, Collection<String> agentNames) {
        List<Mention> all = parseAll(message, agentNames);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * Returns one mention per mentioned agent, in the iteration order of
     * {@code agentNames}. Each agent contributes at most one mention (its
     * first word-boundary occurrence in the message).
     */
    public static List<Mention> parseAll(String message, Collection<String> agentNames) {
        List<Mention> mentions = new java.util.ArrayList<>();
        if (message == null || message.isEmpty()) {
            return mentions;
        }
        String lowerMsg = message.toLowerCase();

        for (String agentName : agentNames) {
            if (agentName == null || agentName.isEmpty()) continue;
            String lowerName = agentName.toLowerCase();

            int idx = indexOfWord(lowerMsg, lowerName);
            if (idx < 0) continue;

            String task = message.substring(idx + lowerName.length()).trim();
            // Strip one leading ',' or ':' (e.g. "bob, come here" / "bob: mine iron")
            if (!task.isEmpty() && (task.charAt(0) == ',' || task.charAt(0) == ':')) {
                task = task.substring(1).trim();
            }
            mentions.add(new Mention(agentName, task));
        }
        return mentions;
    }

    /**
     * Index of the first occurrence of {@code word} in {@code text} at word
     * boundaries (neighbouring characters, if any, are not letters or digits),
     * or -1. Both arguments must already be lower-cased.
     */
    private static int indexOfWord(String text, String word) {
        int from = 0;
        int idx;
        while ((idx = text.indexOf(word, from)) >= 0) {
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return idx;
            }
            from = idx + 1;
        }
        return -1;
    }
}
