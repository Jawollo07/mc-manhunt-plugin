package de.jawollo07.manhunt;

import de.jawollo07.manhunt.arena.ArenaRepository;
import de.jawollo07.manhunt.arena.ArenaService;
import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.game.LobbyMenu;
import de.jawollo07.manhunt.map.MapManager;
import de.jawollo07.manhunt.modes.GameModeFactory;
import de.jawollo07.manhunt.modes.GameModeRegistry;
import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.player.PlayerSessionListener;
import de.jawollo07.manhunt.player.PlayerSnapshotRepository;
import de.jawollo07.manhunt.player.PlayerSnapshotService;
import de.jawollo07.manhunt.storage.ConfigManager;
import de.jawollo07.manhunt.storage.StorageManager;
import de.jawollo07.manhunt.stats.GameStatisticsRepository;
import org.bukkit.command.PluginCommand;
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
    private ArenaRepository arenaRepository;
    private ArenaService arenaService;
    private StorageManager storageManager;
    private GameStatisticsRepository statisticsRepository;
    private ManHuntCommand commandHandler;
    private LobbyMenu lobbyMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        gameManager = new GameManager();
        gameModeRegistry = new GameModeRegistry();
        for (de.jawollo07.manhunt.modes.GameModeFactory factory :
                de.jawollo07.manhunt.modes.builtin.BuiltinGameModes.factories(this)) {
            gameModeRegistry.register(factory);
        }
        playerManager = new PlayerManager();
        mapManager = new MapManager(this);
        storageManager = new StorageManager(this, configManager);

        try {
            storageManager.connect();
            playerSnapshotRepository = new PlayerSnapshotRepository(storageManager);
            playerSnapshotService = new PlayerSnapshotService(playerSnapshotRepository);
            arenaRepository = new ArenaRepository(storageManager);
            arenaService = new ArenaService(arenaRepository);
            statisticsRepository = new GameStatisticsRepository(storageManager);
        } catch (Exception exception) {
            getLogger().severe("Could not initialize the plugin database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        gameLifecycleService = new GameLifecycleService(
                gameManager, playerSnapshotService, playerManager, mapManager, statisticsRepository
        );
        getServer().getPluginManager().registerEvents(
                new PlayerSessionListener(gameManager, gameLifecycleService, playerSnapshotService),
                this
        );

        getServer().getPluginManager().registerEvents(
                new de.jawollo07.manhunt.player.PlayerDeathListener(gameManager, gameLifecycleService),
                this
        );

        getServer().getPluginManager().registerEvents(
                new de.jawollo07.manhunt.modes.builtin.JuggernautAbilityListener(gameManager),
                this
        );

        lobbyMenu = new LobbyMenu(this);
        getServer().getPluginManager().registerEvents(lobbyMenu, this);

        PluginCommand command = getCommand("manhunt");
        if (command == null) {
            getLogger().severe("The /manhunt command is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        commandHandler = new ManHuntCommand(this);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getLogger().info("ManHuntPlugin enabled. Core services initialized.");
    }

    /** Allows companion plugins to register independent, per-round mode factories. */
    public void registerGameMode(GameModeFactory factory) {
        if (gameModeRegistry == null) {
            throw new IllegalStateException("ManHuntPlugin has not been enabled yet");
        }
        gameModeRegistry.register(factory);
    }

    @Override
    public void onDisable() {
        if (commandHandler != null) commandHandler.shutdown();
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
        if (mapManager != null) {
            try {
                mapManager.deleteAllWorkingCopies();
            } catch (Exception exception) {
                getLogger().severe("Could not clean up temporary arena worlds: " + exception.getMessage());
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
    public ArenaRepository getArenaRepository() { return arenaRepository; }
    public ArenaService getArenaService() { return arenaService; }
    public StorageManager getStorageManager() { return storageManager; }
    public GameStatisticsRepository getStatisticsRepository() { return statisticsRepository; }
    public LobbyMenu getLobbyMenu() { return lobbyMenu; }
}
