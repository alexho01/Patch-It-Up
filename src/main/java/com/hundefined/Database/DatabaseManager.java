package com.hundefined.Database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hundefined.config.BotConfig;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager{
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private HikariDataSource dataSource;

    private DatabaseManager() {
        initializeDataSource();
        createTablesIfNotExists();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // Replace the initializeDataSource() method in your DatabaseManager.java with this:

    private void initializeDataSource() {
        try {
            HikariConfig config = new HikariConfig();

            // Use BotConfig to get database settings
            config.setJdbcUrl(BotConfig.getDatabaseUrl());
            config.setUsername(BotConfig.getDatabaseUsername());
            config.setPassword(BotConfig.getDatabasePassword());
            config.setDriverClassName("SQL DRIVER");

            // Connection pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000); // 5 minutes
            config.setConnectionTimeout(30000); // 30 seconds
            config.setLeakDetectionThreshold(60000); // 1 minute

            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully!");
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void createTablesIfNotExists() {
        String[] createTableQueries = {
                """
            CREATE TABLE IF NOT EXISTS patches (
                id INT AUTO_INCREMENT PRIMARY KEY,
                patch_version VARCHAR(20) NOT NULL UNIQUE,
                title VARCHAR(255) NOT NULL,
                release_date DATETIME NOT NULL,
                patch_url VARCHAR(500),
                summary TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """,
                """
            CREATE TABLE IF NOT EXISTS patch_notes (
                id INT AUTO_INCREMENT PRIMARY KEY,
                patch_id INT NOT NULL,
                category VARCHAR(100) NOT NULL,
                subject VARCHAR(255) NOT NULL,
                change_type VARCHAR(50) NOT NULL,
                description TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (patch_id) REFERENCES patches(id) ON DELETE CASCADE
            )
            """,
                """
            CREATE TABLE IF NOT EXISTS server_subscriptions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                guild_id VARCHAR(20) NOT NULL,
                channel_id VARCHAR(20) NOT NULL,
                is_active BOOLEAN DEFAULT TRUE,
                subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY unique_guild_channel (guild_id, channel_id)
            )
            """,
                """
            CREATE TABLE IF NOT EXISTS notification_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                guild_id VARCHAR(20) NOT NULL,
                patch_id INT NOT NULL,
                sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (patch_id) REFERENCES patches(id) ON DELETE CASCADE,
                UNIQUE KEY unique_guild_patch (guild_id, patch_id)
            )
            """
        };

        try (Connection conn = getConnection()) {
            for (String query : createTableQueries) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(query);
                }
            }
            logger.info("Database tables created/verified successfully!");
        } catch (SQLException e) {
            logger.error("Failed to create database tables", e);
            throw new RuntimeException("Table creation failed", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Method to save a new patch to database
    public boolean savePatch(String version, String title, LocalDateTime releaseDate, String url, String summary) {
        String sql = "INSERT INTO patches (patch_version, title, release_date, patch_url, summary) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, version);
            stmt.setString(2, title);
            stmt.setTimestamp(3, Timestamp.valueOf(releaseDate));
            stmt.setString(4, url);
            stmt.setString(5, summary);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Successfully saved patch: {}", version);
                return true;
            }

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate entry
                logger.info("Patch {} already exists in database", version);
            } else {
                logger.error("Error saving patch: {}", version, e);
            }
        }
        return false;
    }

    // Method to get patch by version
    public PatchInfo getPatch(String version) {
        String sql = "SELECT * FROM patches WHERE patch_version = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PatchInfo(
                        rs.getInt("id"),
                        rs.getString("patch_version"),
                        rs.getString("title"),
                        rs.getTimestamp("release_date").toLocalDateTime(),
                        rs.getString("patch_url"),
                        rs.getString("summary")
                );
            }

        } catch (SQLException e) {
            logger.error("Error retrieving patch: {}", version, e);
        }
        return null;
    }

    // Method to get latest patches
    public List<PatchInfo> getLatestPatches(int limit) {
        String sql = "SELECT * FROM patches ORDER BY release_date DESC LIMIT ?";
        List<PatchInfo> patches = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                patches.add(new PatchInfo(
                        rs.getInt("id"),
                        rs.getString("patch_version"),
                        rs.getString("title"),
                        rs.getTimestamp("release_date").toLocalDateTime(),
                        rs.getString("patch_url"),
                        rs.getString("summary")
                ));
            }

        } catch (SQLException e) {
            logger.error("Error retrieving latest patches", e);
        }
        return patches;
    }

    // Method to subscribe a server to patch notifications
    public boolean subscribeServer(String guildId, String channelId) {
        String sql = "INSERT INTO server_subscriptions (guild_id, channel_id) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE is_active = TRUE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, guildId);
            stmt.setString(2, channelId);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Server {} subscribed to patch notifications in channel {}", guildId, channelId);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error subscribing server {} to notifications", guildId, e);
        }
        return false;
    }

    // Method to unsubscribe a server from patch notifications
    public boolean unsubscribeServer(String guildId, String channelId) {
        String sql = "UPDATE server_subscriptions SET is_active = FALSE WHERE guild_id = ? AND channel_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, guildId);
            stmt.setString(2, channelId);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Server {} unsubscribed from patch notifications in channel {}", guildId, channelId);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error unsubscribing server {} from notifications", guildId, e);
        }
        return false;
    }

    // Method to get all subscribed servers
    public List<ServerSubscription> getSubscribedServers() {
        String sql = "SELECT guild_id, channel_id FROM server_subscriptions WHERE is_active = TRUE";
        List<ServerSubscription> subscriptions = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                subscriptions.add(new ServerSubscription(
                        rs.getString("guild_id"),
                        rs.getString("channel_id")
                ));
            }

        } catch (SQLException e) {
            logger.error("Error retrieving subscribed servers", e);
        }
        return subscriptions;
    }

    // Method to check if notification was already sent
    public boolean wasNotificationSent(String guildId, int patchId) {
        String sql = "SELECT 1 FROM notification_history WHERE guild_id = ? AND patch_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, guildId);
            stmt.setInt(2, patchId);

            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            logger.error("Error checking notification history", e);
        }
        return false;
    }

    // Method to mark notification as sent
    public boolean markNotificationSent(String guildId, int patchId) {
        String sql = "INSERT INTO notification_history (guild_id, patch_id) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, guildId);
            stmt.setInt(2, patchId);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Error marking notification as sent", e);
        }
        return false;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    // Inner classes for data transfer
    public static class PatchInfo {
        public final int id;
        public final String version;
        public final String title;
        public final LocalDateTime releaseDate;
        public final String url;
        public final String summary;

        public PatchInfo(int id, String version, String title, LocalDateTime releaseDate, String url, String summary) {
            this.id = id;
            this.version = version;
            this.title = title;
            this.releaseDate = releaseDate;
            this.url = url;
            this.summary = summary;
        }
    }

    public static class ServerSubscription {
        public final String guildId;
        public final String channelId;

        public ServerSubscription(String guildId, String channelId) {
            this.guildId = guildId;
            this.channelId = channelId;
        }
    }
}