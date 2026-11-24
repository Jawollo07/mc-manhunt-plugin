package de.jawollo07.manhunt.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.jawollo07.manhunt.ManHuntPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class StorageManager implements AutoCloseable {
    private final ManHuntPlugin plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;

    public StorageManager(ManHuntPlugin plugin, ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public void connect() throws IOException {
        if (dataSource != null && !dataSource.isClosed()) return;

        String relativePath = configManager.getString("database.file", "data/manhunt.db");
        Path databasePath = plugin.getDataFolder().toPath().resolve(relativePath).normalize();
        Path parent = databasePath.getParent();
        if (parent != null) Files.createDirectories(parent);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ManHunt-SQLite");
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setJdbcUrl("jdbc:sqlite:" + databasePath);
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");

        dataSource = new HikariDataSource(hikari);
        try (Connection connection = dataSource.getConnection()) {
            initializeSchema(connection);
            plugin.getLogger().info("SQLite database connected: " + databasePath);
        } catch (SQLException exception) {
            close();
            throw new IllegalStateException("Could not initialize SQLite database", exception);
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_snapshots (
                        player_uuid TEXT PRIMARY KEY NOT NULL,
                        payload BLOB NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY NOT NULL,
                        applied_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_migrations (version, applied_at)
                    VALUES (1, strftime('%s', 'now') * 1000)
                    """);

            // Migration 2: persistent arena definitions and named spawn points.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS arenas (
                        id TEXT PRIMARY KEY NOT NULL,
                        display_name TEXT NOT NULL,
                        world_name TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS arena_spawn_points (
                        arena_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL,
                        PRIMARY KEY (arena_id, name),
                        FOREIGN KEY (arena_id) REFERENCES arenas(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_migrations (version, applied_at)
                    VALUES (2, strftime('%s', 'now') * 1000)
                    """);

            // Migration 3: immutable game history and per-participant results.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS game_history (
                        game_id TEXT PRIMARY KEY NOT NULL,
                        mode_id TEXT NOT NULL,
                        arena_id TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER NOT NULL,
                        duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
                        end_reason TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS game_participants (
                        game_id TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        is_winner INTEGER NOT NULL CHECK (is_winner IN (0, 1)),
                        PRIMARY KEY (game_id, player_uuid),
                        FOREIGN KEY (game_id) REFERENCES game_history(game_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_game_history_mode_ended
                    ON game_history(mode_id, ended_at DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_game_participants_player
                    ON game_participants(player_uuid, game_id)
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_migrations (version, applied_at)
                    VALUES (3, strftime('%s', 'now') * 1000)
                    """);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new IllegalStateException("Database is not connected");
        }
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
