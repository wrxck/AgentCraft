package com.agentcraft.persistence;

import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

public class PersistenceManager {

    private final Plugin plugin;
    private Connection connection;
    private String jdbcUrl;
    private Properties dbProps;

    public PersistenceManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            // Load connection config - first try fleet secrets, then config.yml
            String host, port, db, user, password;

            Properties fleetSecrets = loadFleetSecrets();
            if (fleetSecrets != null && fleetSecrets.containsKey("POSTGRES_HOST")) {
                host = fleetSecrets.getProperty("POSTGRES_HOST", "shared-postgres");
                port = fleetSecrets.getProperty("POSTGRES_PORT", "5432");
                db = fleetSecrets.getProperty("POSTGRES_DB", "agentcraft");
                user = fleetSecrets.getProperty("POSTGRES_USER", "agentcraft_user");
                password = fleetSecrets.getProperty("POSTGRES_PASSWORD", "");
            } else {
                host = plugin.getConfig().getString("persistence.host", "shared-postgres");
                port = plugin.getConfig().getString("persistence.port", "5432");
                db = plugin.getConfig().getString("persistence.database", "agentcraft");
                user = plugin.getConfig().getString("persistence.user", "agentcraft_user");
                password = plugin.getConfig().getString("persistence.password", "");
            }

            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            dbProps = new Properties();
            dbProps.setProperty("user", user);
            dbProps.setProperty("password", password);
            dbProps.setProperty("connectTimeout", "5");
            dbProps.setProperty("socketTimeout", "30");

            // Load the driver (relocated by shade plugin)
            Class.forName("org.postgresql.Driver");

            connection = DriverManager.getConnection(jdbcUrl, dbProps);
            createTables();

            plugin.getLogger().info("[Persistence] Connected to PostgreSQL at " + host + ":" + port + "/" + db);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Persistence] Failed to connect to PostgreSQL", e);
            return false;
        }
    }

    private Properties loadFleetSecrets() {
        File secretsFile = new File("/minecraft/fleet-secrets.env");
        if (!secretsFile.exists()) return null;

        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(secretsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    // Strip quotes
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    props.setProperty(key, value);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Persistence] Could not read fleet secrets: " + e.getMessage());
            return null;
        }
        return props;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl, dbProps);
        }
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agents (
                    name VARCHAR(32) PRIMARY KEY,
                    profile_id VARCHAR(32) NOT NULL,
                    skin VARCHAR(64) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE PRECISION NOT NULL,
                    y DOUBLE PRECISION NOT NULL,
                    z DOUBLE PRECISION NOT NULL,
                    yaw REAL NOT NULL DEFAULT 0,
                    state VARCHAR(32) NOT NULL DEFAULT 'IDLE',
                    current_task TEXT,
                    created_at TIMESTAMP DEFAULT NOW(),
                    updated_at TIMESTAMP DEFAULT NOW()
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agent_inventory (
                    id SERIAL PRIMARY KEY,
                    agent_name VARCHAR(32) NOT NULL REFERENCES agents(name) ON DELETE CASCADE,
                    material VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 1
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_agent_inventory_name ON agent_inventory(agent_name)
            """);
        }
    }

    // --- Agent CRUD ---

    public void saveAgent(AgentData data) {
        String sql = """
            INSERT INTO agents (name, profile_id, skin, world, x, y, z, yaw, state, current_task, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (name) DO UPDATE SET
                profile_id = EXCLUDED.profile_id,
                skin = EXCLUDED.skin,
                world = EXCLUDED.world,
                x = EXCLUDED.x,
                y = EXCLUDED.y,
                z = EXCLUDED.z,
                yaw = EXCLUDED.yaw,
                state = EXCLUDED.state,
                current_task = EXCLUDED.current_task,
                updated_at = NOW()
        """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, data.name());
            ps.setString(2, data.profileId());
            ps.setString(3, data.skin());
            ps.setString(4, data.world());
            ps.setDouble(5, data.x());
            ps.setDouble(6, data.y());
            ps.setDouble(7, data.z());
            ps.setFloat(8, data.yaw());
            ps.setString(9, data.state());
            ps.setString(10, data.currentTask());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Failed to save agent " + data.name() + ": " + e.getMessage());
        }
    }

    public List<AgentData> loadAllAgents() {
        List<AgentData> agents = new ArrayList<>();
        String sql = "SELECT name, profile_id, skin, world, x, y, z, yaw, state, current_task FROM agents";

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                agents.add(new AgentData(
                        rs.getString("name"),
                        rs.getString("profile_id"),
                        rs.getString("skin"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getString("state"),
                        rs.getString("current_task")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Failed to load agents: " + e.getMessage());
        }
        return agents;
    }

    public void deleteAgent(String name) {
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM agents WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Failed to delete agent " + name + ": " + e.getMessage());
        }
    }

    // --- Inventory ---

    public void saveInventory(String agentName, List<InventoryItem> items) {
        try {
            Connection conn = getConnection();

            // Clear existing inventory
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM agent_inventory WHERE agent_name = ?")) {
                ps.setString(1, agentName);
                ps.executeUpdate();
            }

            if (items.isEmpty()) return;

            // Batch insert
            String sql = "INSERT INTO agent_inventory (agent_name, material, amount) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (InventoryItem item : items) {
                    ps.setString(1, agentName);
                    ps.setString(2, item.material());
                    ps.setInt(3, item.amount());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Failed to save inventory for " + agentName + ": " + e.getMessage());
        }
    }

    public List<InventoryItem> loadInventory(String agentName) {
        List<InventoryItem> items = new ArrayList<>();
        String sql = "SELECT material, amount FROM agent_inventory WHERE agent_name = ?";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, agentName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new InventoryItem(rs.getString("material"), rs.getInt("amount")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Failed to load inventory for " + agentName + ": " + e.getMessage());
        }
        return items;
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Persistence] Error closing connection: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public record AgentData(String name, String profileId, String skin, String world,
                            double x, double y, double z, float yaw,
                            String state, String currentTask) {}

    public record InventoryItem(String material, int amount) {}
}
