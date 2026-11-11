package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleServiceTest {
    @Test
    void invokesModeCallbacksDuringLifecycle() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger ended = new AtomicInteger();

        GameMode mode = new GameMode() {
            @Override public String getId() { return "test"; }
            @Override public String getDisplayName() { return "Test"; }
            @Override public void onPrepare(Game game) { prepared.incrementAndGet(); }
            @Override public void onStart(Game game) { started.incrementAndGet(); }
            @Override public void onEnd(Game game) { ended.incrementAndGet(); }
        };

        GameManager manager = new GameManager();
        Game game = manager.createGame(
                mode,
                new Arena("test", "Test Arena", "test_world"),
                new GameSettings(2, 8, 5, 300, false, true)
        );
        UUID playerOne = UUID.randomUUID();
        UUID playerTwo = UUID.randomUUID();
        manager.addPlayer(playerOne);
        manager.addPlayer(playerTwo);

        GameLifecycleService lifecycle = new GameLifecycleService(manager);
        lifecycle.prepare();
        lifecycle.start();

        Instant now = Instant.now();
        lifecycle.finish(new GameResult(
                game.getId(), GameOutcome.VICTORY, List.of(playerOne),
                List.of(playerOne, playerTwo), now.minusSeconds(30), now,
                Duration.ofSeconds(30), "Test completed"
        ));

        assertEquals(1, prepared.get());
        assertEquals(1, started.get());
        assertEquals(1, ended.get());
        assertEquals(GameState.ENDING, game.getState());
    }
}
