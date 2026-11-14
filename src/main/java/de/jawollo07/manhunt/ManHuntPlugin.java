package de.jawollo07.manhunt;

import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.modes.GameModeRegistry;
import de.jawollo07.manhunt.map.MapManager;
import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.player.PlayerSessionListener;
import de.jawollo07.manhunt.player.PlayerSnapshotRepository;
import de.jawollo07.manhunt.player.PlayerSnapshotService;
import de.jawollo07.manhunt.storage.ConfigManager;
import de.jawollo07.manhunt.storage.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ManHuntPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private GameManager gameManager;
    private GameLifecycleService gameLifecycleService;
    private GameModeRegistry gameModeRegistry;
    private MapManager mapManager;
    private PlayerManager playerManager;
    private PlayerSnapshotService playerSnapshotService;
    private PlayerSnapshotRepository playerSnapshotRepository;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        gameManager = new GameManager();
        gameModeRegistry = new GameModeRegistry();
        playerManager = new PlayerManager();
        mapManager = new MapManager(this);
        storageManager = new StorageManager(this, configManager);

        try {
            storageManager.connect();
            playerSnapshotRepository = new PlayerSnapshotRepository(storageManager);
            playerSnapshotService = new PlayerSnapshotService(playerSnapshotRepository);
        } catch (Exception exception) {
            getLogger().severe("Could not initialize the plugin database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        gameLifecycleService = new GameLifecycleService(gameManager, playerSnapshotService, playerManager);
        getServer().getPluginManager().registerEvents(
                new PlayerSessionListener(gameManager, gameLifecycleService, playerSnapshotService),
                this
        );

        getLogger().info("ManHuntPlugin enabled. Core services initialized.");
    }

    @Override
    public void onDisable() {
        if (playerSnapshotService != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                try {
                    playerSnapshotService.restore(player);
                } catch (RuntimeException exception) {
                    getLogger().severe("Could not restore player " + player.getUniqueId()
                            + " during shutdown: " + exception.getMessage());
                }
            }
        }
        if (storageManager != null) storageManager.close();
        getLogger().info("ManHuntPlugin disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public GameManager getGameManager() { return gameManager; }
    public GameLifecycleService getGameLifecycleService() { return gameLifecycleService; }
    public GameModeRegistry getGameModeRegistry() { return gameModeRegistry; }
    public MapManager getMapManager() { return mapManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public PlayerSnapshotService getPlayerSnapshotService() { return playerSnapshotService; }
    public PlayerSnapshotRepository getPlayerSnapshotRepository() { return playerSnapshotRepository; }
    public StorageManager getStorageManager() { return storageManager; }
}
