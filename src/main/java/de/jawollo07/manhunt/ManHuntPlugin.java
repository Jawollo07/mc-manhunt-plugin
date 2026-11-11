package de.jawollo07.manhunt;

import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.modes.GameModeRegistry;
import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.storage.ConfigManager;
import de.jawollo07.manhunt.storage.StorageManager;
import de.jawollo07.manhunt.teams.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManHuntPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private GameManager gameManager;
    private GameLifecycleService gameLifecycleService;
    private GameModeRegistry gameModeRegistry;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        gameManager = new GameManager();
        gameLifecycleService = new GameLifecycleService(gameManager);
        gameModeRegistry = new GameModeRegistry();
        playerManager = new PlayerManager();
        teamManager = new TeamManager();
        storageManager = new StorageManager(this, configManager);

        try {
            storageManager.connect();
        } catch (Exception exception) {
            getLogger().severe("Could not initialize the plugin database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("ManHuntPlugin enabled. Core services initialized.");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }
        getLogger().info("ManHuntPlugin disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public GameManager getGameManager() { return gameManager; }
    public GameLifecycleService getGameLifecycleService() { return gameLifecycleService; }
    public GameModeRegistry getGameModeRegistry() { return gameModeRegistry; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public StorageManager getStorageManager() { return storageManager; }
}
