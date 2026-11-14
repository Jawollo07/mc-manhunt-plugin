package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.player.PlayerSnapshotService;

import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates mode callbacks with the shared game state machine.
 * Must only be called from the Paper server thread.
 */
public final class GameLifecycleService {
    private final GameManager gameManager;
    private final PlayerSnapshotService snapshotService;
    private final PlayerManager playerManager;

    /**
     * Constructor for core-only use and tests that do not interact with Bukkit players.
     */
    public GameLifecycleService(GameManager gameManager) {
        this(gameManager, null, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        if ((snapshotService == null) != (playerManager == null)) {
            throw new IllegalArgumentException("Snapshot and player services must be provided together");
        }
        this.snapshotService = snapshotService;
        this.playerManager = playerManager;
    }

    public void prepare() {
        Game game = requireGame();
        if (game.getState() != GameState.WAITING
                && game.getState() != GameState.LOBBY
                && game.getState() != GameState.COUNTDOWN) {
            throw new IllegalStateException("Game cannot be prepared from state " + game.getState());
        }
        if (game.getPlayerCount() < game.getSettings().minPlayers()) {
            throw new IllegalStateException("Not enough players to prepare this game");
        }

        captureParticipants(game);
        try {
            game.getMode().onPrepare(game);
            gameManager.transition(GameState.STARTING);
        } catch (RuntimeException exception) {
            restoreOnlineParticipants(game);
            throw exception;
        }
    }

    public void start() {
        Game game = requireGame();
        if (game.getState() != GameState.STARTING) {
            throw new IllegalStateException("Game must be prepared before it can start");
        }
        game.getMode().onStart(game);
        gameManager.transition(GameState.RUNNING);
    }

    public void finish(GameResult result) {
        Game game = requireGame();
        gameManager.finish(result);
        try {
            game.getMode().onEnd(game);
        } finally {
            // Online players are restored immediately. Offline players keep their
            // snapshots until the join listener can restore them on reconnect.
            restoreOnlineParticipants(game);
        }
    }

    public void playerJoined(UUID playerId) {
        Game game = requireGame();
        GamePlayer player = game.getPlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not part of this game: " + playerId)
        );
        player.reconnect();
        game.getMode().onPlayerJoin(game, player);
    }

    public void playerQuit(UUID playerId) {
        Game game = requireGame();
        GamePlayer player = game.getPlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not part of this game: " + playerId)
        );
        player.disconnect();
        game.getMode().onPlayerQuit(game, player);
    }

    public void playerDied(UUID playerId, UUID killerId) {
        Game game = requireGame();
        if (game.getState() != GameState.RUNNING) {
            throw new IllegalStateException("Deaths can only be processed while the game is running");
        }
        GamePlayer player = game.getPlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not part of this game: " + playerId)
        );
        game.getMode().onPlayerDeath(game, player, killerId);
    }

    public void tick(long elapsedSeconds) {
        if (elapsedSeconds < 0) {
            throw new IllegalArgumentException("elapsedSeconds must not be negative");
        }
        Game game = requireGame();
        if (game.getState() == GameState.RUNNING) {
            game.getMode().onTick(game, elapsedSeconds);
        }
    }

    public void beginReset() {
        Game game = requireGame();
        restoreOnlineParticipants(game);
        gameManager.beginReset();
    }

    /**
     * Call only after players have been restored and the temporary map has
     * been unloaded and cleaned up.
     */
    public void completeReset() {
        Game game = requireGame();
        restoreOnlineParticipants(game);
        gameManager.clearFinishedGame();
    }

    private void captureParticipants(Game game) {
        if (snapshotService == null) return;
        for (GamePlayer participant : game.getPlayers()) {
            playerManager.findOnlinePlayer(participant.getUniqueId())
                    .ifPresent(snapshotService::captureIfAbsent);
        }
    }

    private void restoreOnlineParticipants(Game game) {
        if (snapshotService == null) return;
        RuntimeException failure = null;
        for (GamePlayer participant : game.getPlayers()) {
            try {
                playerManager.findOnlinePlayer(participant.getUniqueId())
                        .ifPresent(snapshotService::restore);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("One or more player snapshots could not be restored");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private Game requireGame() {
        return gameManager.getActiveGame().orElseThrow(
                () -> new IllegalStateException("There is no active game")
        );
    }
}
