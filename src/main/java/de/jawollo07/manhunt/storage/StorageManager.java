package de.jawollo07.manhunt.storage;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

import com.zaxxer.hikari.HikariConfig;
import de.jawollo07.manhunt.storage.ConfigManager;

public class StorageManager {
    private final ConfigManager configManager = new ConfigManager();
    public void connect() {
        HikariConfig config = new HikariConfig();
        HikariDataSource ds;
        String dbURL = "";
        config.setJdbcUrl(dbURL);
        config.setUsername(configManager.getString(""));
        config.setPassword(configManager.getString(""));
        config.setMaximumPoolSize(10);
        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection())  {
            
        }
    }
}
