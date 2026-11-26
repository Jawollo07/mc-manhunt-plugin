package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.map.MapInstance;
import de.jawollo07.manhunt.modes.GameMode;
import de.jawollo07.manhunt.teams.TeamManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Game {
    private final UUID id;
    private final Instant createdAt;
    private GameMode mode;
    private Arena arena;
    private final GameSettings settings;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final Map<UUID, String> lobbyTeamPreferences = new LinkedHashMap<>();
    private final java.util.Set<UUID> readyPlayers = new java.util.LinkedHashSet<>();
    private final Map<UUID, String> arenaVotes = new LinkedHashMap<>();
    private final RoleManager roleManager = new RoleManager();
    private final TeamManager teamManager = new TeamManager();

    private GameState state = GameState.WAITING;
    private MapInstance mapInstance;
    private GameResult result;
    private Instant startedAt;
    private Instant endedAt;
    private int countdownRemainingSeconds = -1;

    public Game(UUID id, GameMode mode, Arena arena, GameSettings settings) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public GameMode getMode() { return mode; }
    public Arena getArena() { return arena; }

    public void setArena(Arena arena) {
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Arena can only be changed before the game starts");
        }
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    public boolean isReady(UUID playerId) { return readyPlayers.contains(playerId); }
    public int getReadyCount() { return readyPlayers.size(); }
    public boolean toggleReady(UUID playerId) {
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Readiness can only be changed in the lobby");
        }
        if (!players.containsKey(Objects.requireNonNull(playerId, "playerId"))) {
            throw new IllegalArgumentException("Player is not part of this game");
        }
        if (!readyPlayers.remove(playerId)) {
            readyPlayers.add(playerId);
            return true;
        }
        return false;
    }

    public boolean areAllPlayersReady(java.util.function.Predicate<UUID> online) {
        boolean foundOnline = false;
        for (GamePlayer participant : players.values()) {
            if (!online.test(participant.getUniqueId())) continue;
            foundOnline = true;
            if (!readyPlayers.contains(participant.getUniqueId())) return false;
        }
        return foundOnline;
    }

    public void castArenaVote(UUID playerId, String arenaId) {
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Arena voting is only available in the lobby");
        }
        if (!players.containsKey(Objects.requireNonNull(playerId, "playerId"))) {
            throw new IllegalArgumentException("Player is not part of this game");
        }
        if (arenaId == null || arenaId.isBlank()) throw new IllegalArgumentException("Arena id must not be blank");
        arenaVotes.put(playerId, arenaId.toLowerCase(java.util.Locale.ROOT));
    }

    public Optional<String> getArenaVote(UUID playerId) {
        return Optional.ofNullable(arenaVotes.get(playerId));
    }

    public Map<String, Long> getArenaVoteCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        arenaVotes.values().forEach(id -> counts.merge(id, 1L, Long::sum));
        return Collections.unmodifiableMap(counts);
    }

    public Optional<String> getWinningArenaVote() {
        return arenaVotes.values().stream().collect(java.util.stream.Collectors.groupingBy(
                value -> value, java.util.stream.Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).findFirst();
    }
    public GameSettings getSettings() { return settings; }
    public RoleManager getRoleManager() { return roleManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public GameState getState() { return state; }
    public boolean isRunning() { return state == GameState.RUNNING; }
    public boolean isTerminal() { return state == GameState.RESETTING; }
    public int getCountdownRemainingSeconds() { return countdownRemainingSeconds; }
    public void setCountdownRemainingSeconds(int seconds) {
        if (seconds < -1) throw new IllegalArgumentException("Countdown seconds must be -1 or non-negative");
        this.countdownRemainingSeconds = seconds;
    }

    public Optional<MapInstance> getMapInstance() {
        return Optional.ofNullable(mapInstance);
    }

    public Optional<GameResult> getResult() {
        return Optional.ofNullable(result);
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> getEndedAt() {
        return Optional.ofNullable(endedAt);
    }

    public Optional<Duration> getDuration() {
        if (startedAt == null) return Optional.empty();
        return Optional.of(Duration.between(startedAt, endedAt == null ? Instant.now() : endedAt));
    }

    public Optional<String> getLobbyTeamPreference(UUID playerId) {
        return Optional.ofNullable(lobbyTeamPreferences.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public void setLobbyTeamPreference(UUID playerId, String teamKey) {
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Lobby teams can only be changed before the game starts");
        }
        if (!players.containsKey(Objects.requireNonNull(playerId, "playerId"))) {
            throw new IllegalArgumentException("Player is not part of this game");
        }
        if (teamKey == null || teamKey.isBlank()) lobbyTeamPreferences.remove(playerId);
        else lobbyTeamPreferences.put(playerId, teamKey.toLowerCase(java.util.Locale.ROOT));
    }

    public Collection<GamePlayer> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public Optional<GamePlayer> getPlayer(UUID playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public boolean hasPlayer(UUID playerId) {
        return players.containsKey(playerId);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getAvailableSlots() {
        return Math.max(0, settings.maxPlayers() - players.size());
    }

    public void addPlayer(GamePlayer player) {
        Objects.requireNonNull(player, "player");
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Players cannot join while game state is " + state);
        }
        if (players.containsKey(player.getUniqueId())) {
            throw new IllegalArgumentException("Player is already part of this game");
        }
        if (players.size() >= settings.maxPlayers()) {
            throw new IllegalStateException("Game is full");
        }
        players.put(player.getUniqueId(), player);
    }

    public Optional<GamePlayer> removePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        GamePlayer removed = players.remove(playerId);
        lobbyTeamPreferences.remove(playerId);
        readyPlayers.remove(playerId);
        arenaVotes.remove(playerId);
        if (removed != null) {
            roleManager.remove(playerId);
            teamManager.removePlayer(playerId);
        }
        return Optional.ofNullable(removed);
    }

    void setMapInstance(MapInstance mapInstance) {
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.COUNTDOWN) {
            throw new IllegalStateException("Map can only be assigned before the game starts");
        }
        this.mapInstance = Objects.requireNonNull(mapInstance, "mapInstance");
    }

    void transitionTo(GameState next) {
        Objects.requireNonNull(next, "next");
        if (!isAllowedTransition(state, next)) {
            throw new IllegalStateException("Invalid game state transition: " + state + " -> " + next);
        }

        if (next == GameState.RUNNING) {
            startedAt = Instant.now();
        }
        if (next == GameState.ENDING || next == GameState.RESETTING) {
            endedAt = endedAt == null ? Instant.now() : endedAt;
        }
        state = next;
    }

    void setResult(GameResult result) {
        if (state != GameState.ENDING) {
            throw new IllegalStateException("A result can only be assigned while the game is ending");
        }
        if (this.result != null) {
            throw new IllegalStateException("Game result has already been assigned");
        }
        this.result = Objects.requireNonNull(result, "result");
    }

    private boolean isAllowedTransition(GameState current, GameState next) {
        return switch (current) {
            case WAITING -> next == GameState.LOBBY || next == GameState.COUNTDOWN
                    || next == GameState.STARTING || next == GameState.ENDING;
            case LOBBY -> next == GameState.COUNTDOWN || next == GameState.STARTING
                    || next == GameState.ENDING;
            case COUNTDOWN -> next == GameState.LOBBY || next == GameState.STARTING
                    || next == GameState.ENDING;
            case STARTING -> next == GameState.RUNNING || next == GameState.ENDING;
            case RUNNING -> next == GameState.ENDING;
            case ENDING -> next == GameState.RESETTING;
            case RESETTING -> false;
        };
    }
}
