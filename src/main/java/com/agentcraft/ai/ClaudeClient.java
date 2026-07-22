package com.agentcraft.ai;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ClaudeClient {

    private static final long FORCE_KILL_GRACE_SECONDS = 2;

    private final Plugin plugin;
    private final String cliPath;
    private final Set<Process> liveProcesses = ConcurrentHashMap.newKeySet();

    public ClaudeClient(Plugin plugin) {
        this.plugin = plugin;
        this.cliPath = plugin.getConfig().getString("claude-cli-path", "/usr/local/bin/claude");
    }

    /**
     * Stream a query to Claude CLI, reading stdout line-by-line in stream-json format.
     * Runs as the 'claude' user via sudo to avoid root restrictions on --dangerously-skip-permissions.
     * Prompt is sent via stdin to avoid ARG_MAX limits with long system prompts.
     */
    public CompletableFuture<Integer> streamQuery(String prompt, File workingDir, int maxTurns,
                                                   Consumer<String> lineCallback) {
        String homeDir = workingDir.getAbsolutePath();

        // sudo -n: non-interactive (no password prompt)
        // env HOME=...: set HOME for the claude process
        // Dockerfile sets Defaults !use_pty to prevent PTY allocation
        List<String> command = new ArrayList<>(List.of(
                "sudo", "-n", "-u", "claude",
                "--",
                "env", "HOME=" + homeDir,
                cliPath,
                "--print",
                "--verbose",
                "--output-format", "stream-json",
                "--max-turns", String.valueOf(maxTurns),
                "--dangerously-skip-permissions",
                "--no-session-persistence"
        ));

        return CompletableFuture.supplyAsync(
                () -> runProcess(command, prompt, workingDir, "[Claude]", lineCallback));
    }

    /**
     * Stream a conversational chat query to Claude CLI for NPC chat.
     * Uses --resume for multi-turn sessions, --max-turns 1, and --model for fast responses.
     * Sessions persist for multi-turn conversation memory.
     */
    public CompletableFuture<Integer> streamChat(String prompt, String systemPrompt, File workingDir,
                                                  String sessionId, String model,
                                                  Consumer<String> lineCallback) {
        String homeDir = workingDir.getAbsolutePath();

        List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-n");
        command.add("-u");
        command.add("claude");
        command.add("--");
        command.add("env");
        command.add("HOME=" + homeDir);
        command.add(cliPath);
        command.add("--print");
        command.add("--verbose");
        command.add("--output-format");
        command.add("stream-json");
        command.add("--max-turns");
        command.add("1");
        command.add("--dangerously-skip-permissions");
        command.add("--model");
        command.add(model);
        command.add("--tools");
        command.add("");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            command.add("--system-prompt");
            command.add(systemPrompt);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            command.add("--resume");
            command.add(sessionId);
        }

        plugin.getLogger().info("[Claude Chat] "
                + (sessionId != null ? "(resume: " + sessionId + ")" : "(new session)"));

        return CompletableFuture.supplyAsync(
                () -> runProcess(command, prompt, workingDir, "[Claude Chat]", lineCallback));
    }

    /**
     * Shared runner: starts the process, writes the prompt to stdin, logs stderr
     * on a daemon thread, and feeds stdout lines (NDJSON) to the callback.
     * Package-private so tests can exercise it with a harmless command.
     */
    int runProcess(List<String> command, String prompt, File workingDir,
                   String logPrefix, Consumer<String> lineCallback) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.environment().remove("CLAUDE_CODE_ENTRYPOINT");
            pb.environment().remove("CLAUDECODE");
            pb.redirectErrorStream(false);

            plugin.getLogger().info(logPrefix + " Starting process in " + workingDir.getAbsolutePath());
            process = pb.start();
            liveProcesses.add(process);
            final Process proc = process;

            // Write prompt to stdin and close it so Claude reads it
            try (OutputStream stdin = proc.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }

            // Read stderr on a separate thread for logging
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = err.readLine()) != null) {
                        plugin.getLogger().warning(logPrefix + " stderr] " + line);
                    }
                } catch (Exception e) {
                    // Process likely terminated
                }
            }, "claude-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Read stdout line-by-line (NDJSON)
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCallback.accept(line);
                }
            }

            int exitCode = proc.waitFor();
            plugin.getLogger().info(logPrefix + " Process exited with code " + exitCode);
            return exitCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, logPrefix + " interrupted", e);
            return -1;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, logPrefix + " CLI error", e);
            return -1;
        } finally {
            if (process != null) {
                liveProcesses.remove(process);
                if (process.isAlive()) {
                    process.destroy();
                }
            }
        }
    }

    public void cancel() {
        for (Process p : liveProcesses) {
            if (!p.isAlive()) continue;
            // SIGTERM: sudo relays it to the claude child. SIGKILL
            // (destroyForcibly) is not relayed and would orphan the child.
            p.destroy();
            CompletableFuture.delayedExecutor(FORCE_KILL_GRACE_SECONDS, TimeUnit.SECONDS)
                    .execute(() -> {
                        if (p.isAlive()) {
                            p.destroyForcibly();
                        }
                    });
        }
    }
}
