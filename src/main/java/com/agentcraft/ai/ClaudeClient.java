package com.agentcraft.ai;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ClaudeClient {

    private final Plugin plugin;
    private final String cliPath;
    private Process currentProcess;

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
        return CompletableFuture.supplyAsync(() -> {
            try {
                String homeDir = workingDir.getAbsolutePath();

                // sudo -n: non-interactive (no password prompt)
                // env HOME=...: set HOME for the claude process
                // Dockerfile sets Defaults !use_pty to prevent PTY allocation
                ProcessBuilder pb = new ProcessBuilder(
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
                );

                pb.directory(workingDir);
                pb.environment().remove("CLAUDE_CODE_ENTRYPOINT");
                pb.environment().remove("CLAUDECODE");
                pb.redirectErrorStream(false);

                plugin.getLogger().info("[Claude] Starting process in " + homeDir);
                currentProcess = pb.start();

                // Write prompt to stdin and close it so Claude reads it
                try (OutputStream stdin = currentProcess.getOutputStream()) {
                    stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                }
                plugin.getLogger().info("[Claude] Prompt written to stdin, reading output...");

                // Read stderr on a separate thread for logging
                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader err = new BufferedReader(
                            new InputStreamReader(currentProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = err.readLine()) != null) {
                            plugin.getLogger().warning("[Claude stderr] " + line);
                        }
                    } catch (Exception e) {
                        // Process likely terminated
                    }
                }, "claude-stderr");
                stderrThread.setDaemon(true);
                stderrThread.start();

                // Read stdout line-by-line (NDJSON)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineCallback.accept(line);
                    }
                }

                int exitCode = currentProcess.waitFor();
                plugin.getLogger().info("[Claude] Process exited with code " + exitCode);

                return exitCode;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Claude CLI error", e);
                return -1;
            } finally {
                currentProcess = null;
            }
        });
    }

    /**
     * Stream a conversational chat query to Claude CLI for NPC chat.
     * Uses --resume for multi-turn sessions, --max-turns 1, and --model for fast responses.
     * Sessions persist for multi-turn conversation memory.
     */
    public CompletableFuture<Integer> streamChat(String prompt, String systemPrompt, File workingDir,
                                                  String sessionId, String model,
                                                  Consumer<String> lineCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(workingDir);
                pb.environment().remove("CLAUDE_CODE_ENTRYPOINT");
                pb.environment().remove("CLAUDECODE");
                pb.redirectErrorStream(false);

                plugin.getLogger().info("[Claude Chat] Starting chat process in " + homeDir
                        + (sessionId != null ? " (resume: " + sessionId + ")" : " (new session)"));
                currentProcess = pb.start();

                // Write prompt to stdin
                try (OutputStream stdin = currentProcess.getOutputStream()) {
                    stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                }

                // Read stderr on a separate thread
                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader err = new BufferedReader(
                            new InputStreamReader(currentProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = err.readLine()) != null) {
                            plugin.getLogger().warning("[Claude Chat stderr] " + line);
                        }
                    } catch (Exception e) {
                        // Process likely terminated
                    }
                }, "claude-chat-stderr");
                stderrThread.setDaemon(true);
                stderrThread.start();

                // Read stdout line-by-line (NDJSON)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lineCallback.accept(line);
                    }
                }

                int exitCode = currentProcess.waitFor();
                plugin.getLogger().info("[Claude Chat] Process exited with code " + exitCode);
                return exitCode;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Claude Chat CLI error", e);
                return -1;
            } finally {
                currentProcess = null;
            }
        });
    }

    public void cancel() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }
}
