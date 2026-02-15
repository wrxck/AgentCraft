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
                    current.onStart();
                }

                ActionResult result = current.tick();
                switch (result) {
                    case SUCCESS -> {
                        plugin.getLogger().info("[ActionQueue] Completed: " + current.getClass().getSimpleName());
                        current.onComplete();
                        current = null;
                    }
                    case FAILED -> {
                        plugin.getLogger().info("[ActionQueue] Failed: " + current.getClass().getSimpleName());
                        current.onFail();
                        current = null;
                    }
                    case CONTINUE -> {} // keep ticking
                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, 1L);
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
