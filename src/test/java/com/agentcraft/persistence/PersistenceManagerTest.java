package com.agentcraft.persistence;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PersistenceManagerTest {

    private PersistenceManager manager;
    private Connection connection;
    private PreparedStatement deleteStmt;
    private PreparedStatement insertStmt;

    @BeforeEach
    void setUp() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PersistenceManagerTest"));
        manager = spy(new PersistenceManager(plugin));

        connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(true);

        deleteStmt = mock(PreparedStatement.class);
        insertStmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("DELETE FROM agent_inventory"))).thenReturn(deleteStmt);
        when(connection.prepareStatement(startsWith("INSERT INTO agent_inventory"))).thenReturn(insertStmt);

        setConnection(connection);
    }

    private void setConnection(Connection conn) throws Exception {
        Field field = PersistenceManager.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(manager, conn);
    }

    @Test
    void saveInventoryRunsInATransactionAndCommits() throws Exception {
        manager.saveInventory("Grumble", List.of(
                new PersistenceManager.InventoryItem("COBBLESTONE", 32),
                new PersistenceManager.InventoryItem("OAK_LOG", 5)));

        InOrder order = inOrder(connection, deleteStmt, insertStmt);
        order.verify(connection).setAutoCommit(false);
        order.verify(deleteStmt).executeUpdate();
        order.verify(insertStmt).executeBatch();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
    }

    @Test
    void saveInventoryRollsBackWhenInsertFails() throws Exception {
        when(insertStmt.executeBatch()).thenThrow(new SQLException("boom"));

        manager.saveInventory("Grumble", List.of(
                new PersistenceManager.InventoryItem("COBBLESTONE", 32)));

        InOrder order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).commit();
    }

    @Test
    void saveInventoryEmptyListStillCommitsTheDelete() throws Exception {
        manager.saveInventory("Grumble", List.of());

        InOrder order = inOrder(connection, deleteStmt);
        order.verify(connection).setAutoCommit(false);
        order.verify(deleteStmt).executeUpdate();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void deadConnectionTriggersReconnect() throws Exception {
        // A dead TCP connection is not "closed", but it is no longer valid.
        when(connection.isValid(anyInt())).thenReturn(false);
        Connection fresh = mock(Connection.class);
        doReturn(fresh).when(manager).openConnection();

        Connection result = manager.getConnection();

        assertSame(fresh, result, "invalid connection must be replaced by a fresh one");
    }

    @Test
    void healthyConnectionIsReused() throws Exception {
        Connection result = manager.getConnection();

        assertSame(connection, result);
        verify(manager, never()).openConnection();
    }
}
