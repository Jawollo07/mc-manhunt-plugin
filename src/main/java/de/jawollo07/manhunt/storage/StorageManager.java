package de.jawollo07.manhunt.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.jawollo07.manhunt.ManHuntPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        String relativePath = configManager.getString("database.file", "data/manhunt.db");
        Path databasePath = plugin.getDataFolder().toPath().resolve(relativePath).normalize();
        Path parent = databasePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ManHunt-SQLite");
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setJdbcUrl("jdbc:sqlite:" + databasePath);
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");

        dataSource = new HikariDataSource(hikari);
        try (Connection ignored = dataSource.getConnection()) {
            plugin.getLogger().info("SQLite database connected: " + databasePath);
        } catch (SQLException exception) {
            close();
            throw new IllegalStateException("Could not connect to SQLite database", exception);
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
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
