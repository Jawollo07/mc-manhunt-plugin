package de.jawollo07.manhunt.game;

import java.lang.foreign.Arena;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;

public final class Game {
    private final UUID id;
    private final Instant createdAt;

    private final GameMode mode;
    private final Arena arena;
    private final GameSettings settings;

    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();

    private GameState state = GameState.WAITING;

    private MapInstance mapInstance;
    private GameResult result;

    private Instant startedAt;
    private Instant endedAt;

    public Game(
            UUID id,
            GameMode mode,
            Arena arena,
            GameSettings settings
    ) {
        this.id = id;
        this.createdAt = Instant.now();

        this.mode = mode;
        this.arena = arena;
        this.settings = settings;
    }
}
