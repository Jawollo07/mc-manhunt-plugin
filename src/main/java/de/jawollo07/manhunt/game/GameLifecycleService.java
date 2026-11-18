package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.arena.ArenaSpawnPoint;
import de.jawollo07.manhunt.map.MapInstance;
import de.jawollo07.manhunt.map.MapManager;
import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.player.PlayerSnapshotService;
import de.jawollo07.manhunt.teams.Team;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates the shared game state machine, disposable map lifecycle and mode callbacks.
 * Must only be called from the Paper server thread.
 */
public final class GameLifecycleService {
    private final GameManager gameManager;
    private final PlayerSnapshotService snapshotService;
    private final PlayerManager playerManager;
    private final MapManager mapManager;

    /** Constructor for core-only use and tests that do not interact with Bukkit. */
    public GameLifecycleService(GameManager gameManager) {
        this(gameManager, null, null, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager
    ) {
        this(gameManager, snapshotService, playerManager, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager,
            MapManager mapManager
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        if ((snapshotService == null) != (playerManager == null)) {
            throw new IllegalArgumentException("Snapshot and player services must be provided together");
        }
        this.snapshotService = snapshotService;
        this.playerManager = playerManager;
        this.mapManager = mapManager;
    }

    /**
     * Captures players, creates an isolated arena world, then lets the mode prepare
     * its roles and teams. The arena's worldName is the map-template folder ID.
     */
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
            if (mapManager != null && game.getMapInstance().isEmpty()) {
                MapInstance instance = mapManager.createWorkingCopy(game.getArena().worldName());
                gameManager.assignMapInstance(instance);
            }
            game.getMode().onPrepare(game);
            gameManager.transition(GameState.STARTING);
        } catch (RuntimeException | IOException exception) {
            try {
                restoreOnlineParticipants(game);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            try {
                deleteWorkingMap(game);
            } catch (RuntimeException | IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            // A failed mode preparation may already have assigned partial teams
            // and roles. The same lobby session can be retried, so clear those
            // generic per-round assignments before returning control to it.
            game.getTeamManager().clear();
            game.getRoleManager().clear();
            throw exception instanceof RuntimeException runtime
                    ? runtime : new IllegalStateException("Could not prepare the game map", exception);
        }
    }

    /**
     * Sends each participant to their configured team spawn, falling back to the
     * arena's default spawn and finally the temporary world's natural spawn.
     */
    public void start() {
        Game game = requireGame();
        if (game.getState() != GameState.STARTING) {
            throw new IllegalStateException("Game must be prepared before it can start");
        }

        try {
            if (mapManager != null && game.getMapInstance().isPresent()) {
                World world = mapManager.getWorld(game.getMapInstance().orElseThrow());
                for (GamePlayer participant : game.getPlayers()) {
                    playerManager.findOnlinePlayer(participant.getUniqueId()).ifPresent(player -> {
                        Location spawn = resolveSpawn(game, participant, world);
                        if (!player.teleport(spawn)) {
                            throw new IllegalStateException("Could not teleport player " + player.getUniqueId()
                                    + " into the arena");
                        }
                    });
                }
            }
            game.getMode().onStart(game);
            gameManager.transition(GameState.RUNNING);
        } catch (RuntimeException exception) {
            try {
                restoreOnlineParticipants(game);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
    }

    private Location resolveSpawn(Game game, GamePlayer participant, World world) {
        Arena arena = game.getArena();
        Team team = game.getTeamManager().getTeamOf(participant.getUniqueId()).orElse(null);
        if (team != null) {
            String teamKey = "team:" + slug(team.getName());
            ArenaSpawnPoint teamSpawn = arena.spawnPoint(teamKey).orElse(null);
            if (teamSpawn != null) return teamSpawn.toLocation(world);
        }
        return arena.spawnPoint("default")
                .map(point -> point.toLocation(world))
                .orElseGet(world::getSpawnLocation);
    }

    private String slug(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    public void finish(GameResult result) {
        Game game = requireGame();
        gameManager.finish(result);
        try {
            game.getMode().onEnd(game);
        } finally {
            // Offline players keep their durable snapshots until reconnect.
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
     * Restores players, unloads/deletes the disposable world, and only then
     * releases the single active game slot. If cleanup fails, the game remains
     * registered in RESETTING so cleanup can safely be retried.
     */
    public void completeReset() {
        Game game = requireGame();
        restoreOnlineParticipants(game);
        try {
            deleteWorkingMap(game);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete the temporary arena world", exception);
        }
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

    private void deleteWorkingMap(Game game) throws IOException {
        if (mapManager == null || game.getMapInstance().isEmpty()) return;
        mapManager.deleteWorkingCopy(game.getMapInstance().orElseThrow());
    }

    private Game requireGame() {
        return gameManager.getActiveGame().orElseThrow(
                () -> new IllegalStateException("There is no active game")
        );
    }
}
