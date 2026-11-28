package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.arena.ArenaBounds;
import de.jawollo07.manhunt.arena.ArenaSpawnPoint;
import de.jawollo07.manhunt.arena.StartInventoryData;
import de.jawollo07.manhunt.arena.ArenaValidation;
import de.jawollo07.manhunt.map.MapInstance;
import de.jawollo07.manhunt.map.MapManager;
import de.jawollo07.manhunt.player.PlayerManager;
import de.jawollo07.manhunt.player.PlayerSnapshotService;
import de.jawollo07.manhunt.stats.GameStatisticsRepository;
import de.jawollo07.manhunt.teams.Team;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.sql.SQLException;
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
    private final GameStatisticsRepository statisticsRepository;

    /** Constructor for core-only use and tests that do not interact with Bukkit. */
    public GameLifecycleService(GameManager gameManager) {
        this(gameManager, null, null, null, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager
    ) {
        this(gameManager, snapshotService, playerManager, null, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager,
            MapManager mapManager
    ) {
        this(gameManager, snapshotService, playerManager, mapManager, null);
    }

    public GameLifecycleService(
            GameManager gameManager,
            PlayerSnapshotService snapshotService,
            PlayerManager playerManager,
            MapManager mapManager,
            GameStatisticsRepository statisticsRepository
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        if ((snapshotService == null) != (playerManager == null)) {
            throw new IllegalArgumentException("Snapshot and player services must be provided together");
        }
        this.snapshotService = snapshotService;
        this.playerManager = playerManager;
        this.mapManager = mapManager;
        this.statisticsRepository = statisticsRepository;
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

        if (ArenaBounds.hasAny(game.getArena()) && ArenaBounds.from(game.getArena()).isEmpty()) {
            throw new IllegalStateException("Die Arena-Begrenzung ist unvollständig. Setze beide Eckpunkte oder entferne die Begrenzung.");
        }

        ArenaValidation spawnValidation = ArenaValidation.check(game.getArena(), game.getMode(), true, false);
        if (!spawnValidation.ready()) {
            throw new IllegalStateException(String.join(" ", spawnValidation.errors()));
        }

        captureParticipants(game);
        try {
            if (mapManager != null && game.getMapInstance().isEmpty()) {
                MapInstance instance = mapManager.createWorkingCopy(game.getArena().worldName());
                gameManager.assignMapInstance(instance);
            }
            game.getMode().onPrepare(game);
            if (mapManager != null && game.getMapInstance().isPresent()) {
                World world = mapManager.getWorld(game.getMapInstance().orElseThrow());
                ArenaBounds bounds = ArenaBounds.from(game.getArena()).orElse(null);
                if (bounds != null) {
                    bounds.applyWorldBorder(world);
                    for (GamePlayer participant : game.getPlayers()) {
                        Location spawn = resolveSpawn(game, participant, world);
                        if (!bounds.contains(spawn)) {
                            throw new IllegalStateException("Spawnpunkt für " + participant.getUniqueId()
                                    + " liegt außerhalb der Arena-Begrenzung.");
                        }
                    }
                }
            }
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
                        applyTeamStartInventory(game, participant, player);
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

    /**
     * Resolves the arena respawn location for an in-round death. Converted
     * Infection players use their new team's spawn; eliminated spectators
     * return to the neutral arena spawn.
     */
    public Location getRespawnLocation(UUID playerId) {
        Game game = requireGame();
        if (game.getState() != GameState.RUNNING || mapManager == null || game.getMapInstance().isEmpty()) {
            return null;
        }
        GamePlayer participant = game.getPlayer(playerId).orElse(null);
        if (participant == null) return null;
        World world = mapManager.getWorld(game.getMapInstance().orElseThrow());
        if (participant.isEliminated()) {
            return game.getArena().spawnPoint("default")
                    .map(point -> point.toLocation(world))
                    .orElseGet(world::getSpawnLocation);
        }
        return resolveSpawn(game, participant, world);
    }

    private void applyTeamStartInventory(Game game, GamePlayer participant, Player player) {
        var inventory = player.getInventory();
        inventory.clear();

        Team team = game.getTeamManager().getTeamOf(participant.getUniqueId()).orElse(null);
        String role = game.getRoleManager().getRoleId(participant.getUniqueId()).orElse("");
        String teamKey = team == null ? "" : slug(team.getName());
        String roleKey = switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "infected" -> "infected";
            case "survivor" -> "survivors";
            case "hitman" -> "hitmen";
            case "juggernaut" -> "juggernaut";
            case "hunter" -> "hunters";
            case "assassin" -> "assassins";
            default -> "";
        };

        String encoded = roleKey.isBlank() ? "" : game.getArena().modeSetting("loadouts", roleKey, "");
        String selectedKey = roleKey;
        if (encoded.isBlank() && !teamKey.isBlank()) {
            encoded = game.getArena().modeSetting("loadouts", teamKey, "");
            selectedKey = teamKey;
        }
        if (encoded.isBlank()) {
            if (team == null) {
                pluginLogger().warning("Kein Team für " + player.getName()
                        + " beim Anwenden des Startinventars in Arena " + game.getArena().id()
                        + " (Rolle: " + role + ")");
            }
            return;
        }

        try {
            StartInventoryData loadout = StartInventoryData.decode(encoded);
            inventory.setContents(loadout.contents());
            inventory.setArmorContents(loadout.armor());
            inventory.setExtraContents(loadout.extra());
            player.updateInventory();
            pluginLogger().info("Startinventar '" + selectedKey + "' aus Arena "
                    + game.getArena().id() + " an " + player.getName() + " ausgegeben.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Startinventar für Team " + selectedKey
                    + " in Arena " + game.getArena().id() + " ist beschädigt", exception);
        }
    }

    private java.util.logging.Logger pluginLogger() {
        return org.bukkit.Bukkit.getLogger();
    }

    private Location resolveSpawn(Game game, GamePlayer participant, World world) {
        Arena arena = game.getArena();
        Team team = game.getTeamManager().getTeamOf(participant.getUniqueId()).orElse(null);
        if (team != null) {
            String teamKey = "team:" + slug(team.getName());
            java.util.List<ArenaSpawnPoint> teamSpawns = arena.spawnPoints().values().stream()
                    .filter(point -> point.name().equalsIgnoreCase(teamKey)
                            || point.name().toLowerCase(java.util.Locale.ROOT).startsWith(teamKey + ":"))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            if (!teamSpawns.isEmpty()) {
                java.util.Collections.shuffle(teamSpawns);
                return teamSpawns.get(0).toLocation(world);
            }
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
        // Multiple mode callbacks must not finalize the same round twice.
        if (game.getState() == GameState.ENDING || game.getState() == GameState.RESETTING) return;
        gameManager.finish(result);
        try {
            game.getMode().onEnd(game);
        } finally {
            try {
                if (statisticsRepository != null) {
                    statisticsRepository.recordGame(game, result);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not persist game history and statistics", exception);
            } finally {
                // Offline players keep their durable snapshots until reconnect.
                restoreOnlineParticipants(game);
            }
        }
    }

    public void playerJoined(UUID playerId) {
        Game game = requireGame();
        GamePlayer participant = game.getPlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not part of this game: " + playerId)
        );
        if (participant.isConnected()) return;
        expireDisconnectedParticipant(game, participant, java.time.Instant.now());
        participant.reconnect();

        // Re-enter the disposable arena instead of restoring the pre-game
        // snapshot. Eliminated players return as spectators at the neutral spawn.
        if (game.getState() == GameState.RUNNING && mapManager != null && playerManager != null) {
            Player online = playerManager.findOnlinePlayer(playerId).orElse(null);
            if (online != null) {
                Location destination = getRespawnLocation(playerId);
                if (destination != null && !online.teleport(destination)) {
                    throw new IllegalStateException("Could not return reconnecting player to the arena: " + playerId);
                }
                if (participant.isEliminated()) {
                    online.setGameMode(org.bukkit.GameMode.SPECTATOR);
                }
            }
        }
        game.getMode().onPlayerJoin(game, participant);
    }

    public void playerQuit(UUID playerId) {
        Game game = requireGame();
        GamePlayer player = game.getPlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not part of this game: " + playerId)
        );
        // A disconnect during STARTING must preserve a live round status:
        // BaseMode.onStart runs after the player may already have left.
        if (game.getState() == GameState.STARTING && !player.isEliminated()) {
            player.setStatus(de.jawollo07.manhunt.player.PlayerStatus.PLAYING);
        }
        // Keep the participant alive during the arena-specific grace period.
        // Mode callbacks run only if the player fails to return in time.
        player.disconnect();
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
            expireDisconnectedParticipants(game, java.time.Instant.now());
            if (game.getState() == GameState.RUNNING) {
                game.getMode().onTick(game, elapsedSeconds);
            }
        }
    }

    private void expireDisconnectedParticipants(Game game, java.time.Instant now) {
        int graceSeconds = game.getArena().modeIntSetting("general", "disconnect_grace_seconds", 60);
        if (graceSeconds <= 0) return;
        for (GamePlayer participant : game.getPlayers()) {
            if (!participant.hasExceededDisconnectGrace(java.time.Duration.ofSeconds(graceSeconds), now)) continue;
            if (expireDisconnectedParticipant(game, participant, now)
                    && game.getState() != GameState.RUNNING) return;
        }
    }

    private boolean expireDisconnectedParticipant(Game game, GamePlayer participant, java.time.Instant now) {
        int graceSeconds = game.getArena().modeIntSetting("general", "disconnect_grace_seconds", 60);
        if (graceSeconds <= 0
                || !participant.hasExceededDisconnectGrace(java.time.Duration.ofSeconds(graceSeconds), now)) {
            return false;
        }
        if (!participant.expireDisconnect()) return false;
        game.getMode().onPlayerQuit(game, participant);
        return true;
    }

    public void beginReset() {
        Game game = requireGame();
        if (game.getState() == GameState.RESETTING) return;
        if (game.getState() != GameState.ENDING) {
            throw new IllegalStateException("Only an ending game can begin reset");
        }
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
