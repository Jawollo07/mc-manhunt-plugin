package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameManagerTest {
    private final GameMode mode = new GameMode() {
        @Override public String getId() { return "assassin"; }
        @Override public String getDisplayName() { return "Assassin Manhunt"; }
    };

    private final Arena arena = new Arena("forest", "Forest", "manhunt_forest");
    private final GameSettings settings = new GameSettings(2, 8, 30, 2400, false, true);

    @Test
    void createsOnlyOneActiveGame() {
        GameManager manager = new GameManager();
        manager.createGame(mode, arena, settings);

        assertThrows(IllegalStateException.class,
                () -> manager.createGame(mode, arena, settings));
    }

    @Test
    void rejectsStartingWithTooFewPlayers() {
        GameManager manager = new GameManager();
        manager.createGame(mode, arena, settings);
        manager.addPlayer(UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> manager.transition(GameState.STARTING));
    }

    @Test
    void completesGameLifecycle() {
        GameManager manager = new GameManager();
        Game game = manager.createGame(mode, arena, settings);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.addPlayer(first);
        manager.addPlayer(second);

        manager.transition(GameState.LOBBY);
        manager.transition(GameState.COUNTDOWN);
        manager.transition(GameState.STARTING);
        manager.transition(GameState.RUNNING);

        Instant endedAt = Instant.now();
        Instant startedAt = endedAt.minusSeconds(120);
        manager.finish(new GameResult(
                game.getId(),
                GameOutcome.VICTORY,
                List.of(first),
                List.of(first, second),
                startedAt,
                endedAt,
                Duration.ofSeconds(120),
                "Last player standing"
        ));

        assertEquals(GameState.ENDING, game.getState());
        assertTrue(game.getResult().isPresent());

        manager.beginReset();
        manager.clearFinishedGame();

        assertFalse(manager.hasActiveGame());
        assertTrue(manager.getActiveGame().isEmpty());
    }

    @Test
    void rejectsResultForAnotherGame() {
        GameManager manager = new GameManager();
        manager.createGame(mode, arena, settings);

        GameResult foreignResult = new GameResult(
                UUID.randomUUID(),
                GameOutcome.CANCELLED,
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now(),
                Duration.ZERO,
                "Foreign result"
        );

        assertThrows(IllegalArgumentException.class, () -> manager.finish(foreignResult));
        assertEquals(GameState.WAITING, manager.getActiveGame().orElseThrow().getState());
    }
}
