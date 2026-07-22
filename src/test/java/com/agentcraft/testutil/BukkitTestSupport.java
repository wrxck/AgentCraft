package com.agentcraft.testutil;

import java.lang.reflect.Field;
import org.bukkit.Bukkit;
import org.bukkit.Server;

/**
 * Installs a mock {@link Server} into the {@link Bukkit} singleton for unit
 * tests. Bukkit only allows the server to be set once, so tests must go
 * through reflection to swap or clear it (the same approach MockBukkit uses).
 */
public final class BukkitTestSupport {

    private BukkitTestSupport() {
    }

    public static void setServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to install test server", e);
        }
    }

    public static void clearServer() {
        setServer(null);
    }
}
