package com.agentcraft.action;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.LinkedList;
import java.util.Queue;

public class ActionQueue {

    private final Plugin plugin;
    private final Queue<Action> queue = new LinkedList<>();
    private BukkitRunnable runnable;
    private Action current;
    private int delayTicks;
    private int tickCounter;
    private Runnable onComplete;

    public ActionQueue(Plugin plugin) {
        this.plugin = plugin;
        this.delayTicks = plugin.getConfig().getInt("action-delay-ticks", 4);
    }

    public void add(Action action) {
        queue.add(action);
    }

    public void start() {
        if (runnable != null) {
            plugin.getLogger().info("[ActionQueue] start() called but already running (current="
                + (current != null ? current.getClass().getSimpleName() : "null") + ", queued=" + queue.size() + ")");
            return;
        }

        tickCounter = 0;
        runnable = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                if (tickCounter < delayTicks) return;
                tickCounter = 0;

                if (current == null) {
                    current = queue.poll();
                    if (current == null) {
                        stop();
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    plugin.getLogger().info("[ActionQueue] Starting: " + current.getClass().getSimpleName());
                    try {
                        current.onStart();
                    } catch (Exception e) {
                        plugin.getLogger().warning("[ActionQueue] onStart() crashed for "
                                + current.getClass().getSimpleName() + ": " + e.getMessage());
                        failCurrentQuietly();
                        return;
                    }
                }

                ActionResult result;
                try {
                    result = current.tick();
                } catch (Exception e) {
                    plugin.getLogger().warning("[ActionQueue] tick() crashed for "
                            + current.getClass().getSimpleName() + ": " + e.getMessage());
                    failCurrentQuietly();
                    return;
                }

                switch (result) {
                    case SUCCESS -> {
                        plugin.getLogger().info("[ActionQueue] Completed: " + current.getClass().getSimpleName());
                        try { current.onComplete(); } catch (Exception e) {
                            plugin.getLogger().warning("[ActionQueue] onComplete() error: " + e.getMessage());
                        }
                        current = null;
                    }
                    case FAILED -> {
                        plugin.getLogger().info("[ActionQueue] Failed: " + current.getClass().getSimpleName());
                        try { current.onFail(); } catch (Exception e) {
                            plugin.getLogger().warning("[ActionQueue] onFail() error: " + e.getMessage());
                        }
                        current = null;
                    }
                    case CONTINUE -> {} // keep ticking
                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Give a crashed action its onFail() cleanup (stopping break animations,
     * cancelling navigation, ...) before discarding it.
     */
    private void failCurrentQuietly() {
        try {
            current.onFail();
        } catch (Exception e) {
            plugin.getLogger().warning("[ActionQueue] onFail() error: " + e.getMessage());
        }
        current = null;
    }

    public void stop() {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
        current = null;
    }

    public void clear() {
        stop();
        queue.clear();
    }

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    public int size() {
        return queue.size() + (current != null ? 1 : 0);
    }

    public boolean isRunning() {
        return runnable != null;
    }
}
