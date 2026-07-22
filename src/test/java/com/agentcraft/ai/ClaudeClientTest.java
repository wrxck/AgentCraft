package com.agentcraft.ai;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeClientTest {

    /** Command that closes its stdout/stderr immediately, then sleeps. This lets
     *  runProcess reach waitFor() while the process is still alive. */
    private static final List<String> SLEEPER =
            List.of("/bin/sh", "-c", "exec 1>&- 2>&-; sleep 8");

    @TempDir
    File workDir;

    private ClaudeClient client;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getString(eq("claude-cli-path"), anyString()))
                .thenReturn("/usr/local/bin/claude");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ClaudeClientTest"));
        client = new ClaudeClient(plugin);
    }

    @Test
    @Timeout(15)
    void runnerEchoesStdoutLinesAndExitCode() {
        List<String> lines = new CopyOnWriteArrayList<>();
        int exit = client.runProcess(List.of("/bin/cat"), "hello\nworld\n",
                workDir, "[Test]", lines::add);
        assertEquals(0, exit);
        assertEquals(List.of("hello", "world"), lines);
    }

    @Test
    @Timeout(20)
    void concurrentCallsDoNotClobberEachOthersHandles() throws Exception {
        // Long-running call A
        CompletableFuture<Integer> callA = CompletableFuture.supplyAsync(
                () -> client.runProcess(SLEEPER, "", workDir, "[A]", l -> { }));
        Thread.sleep(500); // let A's process start

        // Short call B runs and finishes while A is still alive
        int exitB = client.runProcess(List.of("/bin/cat"), "x", workDir, "[B]", l -> { });
        assertEquals(0, exitB);

        // cancel() must still be able to terminate A, even though B finished after it
        client.cancel();
        int exitA = callA.get(5, TimeUnit.SECONDS);
        assertNotEquals(0, exitA, "cancelled process should not exit cleanly");
    }

    @Test
    @Timeout(20)
    void cancelTerminatesAllRunningCalls() throws Exception {
        CompletableFuture<Integer> callA = CompletableFuture.supplyAsync(
                () -> client.runProcess(SLEEPER, "", workDir, "[A]", l -> { }));
        CompletableFuture<Integer> callB = CompletableFuture.supplyAsync(
                () -> client.runProcess(SLEEPER, "", workDir, "[B]", l -> { }));
        Thread.sleep(500);

        client.cancel();
        assertNotEquals(0, callA.get(5, TimeUnit.SECONDS));
        assertNotEquals(0, callB.get(5, TimeUnit.SECONDS));
    }

    @Test
    @Timeout(20)
    void interruptRestoresFlagAndDestroysProcess() throws Exception {
        AtomicBoolean interruptFlagRestored = new AtomicBoolean(false);
        AtomicInteger exitCode = new AtomicInteger(0);

        Thread worker = new Thread(() -> {
            int code = client.runProcess(SLEEPER, "", workDir, "[Test]", l -> { });
            exitCode.set(code);
            interruptFlagRestored.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(500); // let it reach waitFor()
        worker.interrupt();
        worker.join(5000);

        assertTrue(!worker.isAlive(), "worker should finish promptly after interrupt");
        assertEquals(-1, exitCode.get());
        assertTrue(interruptFlagRestored.get(),
                "InterruptedException must restore the thread's interrupt flag");
    }
}
