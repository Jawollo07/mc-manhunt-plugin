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
    void finishingTheSameRoundTwiceRunsEndCallbackOnlyOnce() {
        AtomicInteger ended = new AtomicInteger();
        GameMode mode = new GameMode() {
            @Override public String getId() { return "test"; }
            @Override public String getDisplayName() { return "Test"; }
            @Override public void onEnd(Game game) { ended.incrementAndGet(); }
        };
        GameManager manager = new GameManager();
        Game game = manager.createGame(mode, new Arena("test", "Test Arena", "test_world"),
                new GameSettings(2, 8, 5, 300, false, true));
        GameLifecycleService lifecycle = new GameLifecycleService(manager);
        Instant now = Instant.now();
        GameResult result = new GameResult(game.getId(), GameOutcome.CANCELLED, List.of(),
                List.of(), now, now, Duration.ZERO, "Test");

        lifecycle.finish(result);
        lifecycle.finish(result);

        assertEquals(GameState.ENDING, game.getState());
        assertEquals(1, ended.get());
    }

    @Test
    void expiresDisconnectedParticipantsAfterTheArenaGracePeriod() {
        AtomicInteger quitCallbacks = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        GameMode mode = new GameMode() {
            @Override public String getId() { return "test"; }
            @Override public String getDisplayName() { return "Test"; }
            @Override public void onPlayerQuit(Game game, GamePlayer player) { quitCallbacks.incrementAndGet(); }
            @Override public void onTick(Game game, long elapsedSeconds) { ticks.incrementAndGet(); }
        };
        GameManager manager = new GameManager();
        Game game = manager.createGame(mode, new Arena("test", "Test Arena", "test_world"),
                new GameSettings(1, 2, 0, 300, false, true));
        GamePlayer player = manager.addPlayer(UUID.randomUUID());
        player.setStatus(de.jawollo07.manhunt.player.PlayerStatus.PLAYING);
        player.disconnect(Instant.now().minusSeconds(61));
        manager.transition(GameState.STARTING);
        manager.transition(GameState.RUNNING);

        new GameLifecycleService(manager).tick(1);

        assertEquals(1, quitCallbacks.get());
        assertEquals(1, ticks.get());
        assertTrue(player.isEliminated());
        assertFalse(player.isActiveForRound());
        assertFalse(player.isConnected());
    }

    @Test
    void disabledArenaDisconnectGraceKeepsDisconnectedPlayerAlive() {
        AtomicInteger quitCallbacks = new AtomicInteger();
        GameMode mode = new GameMode() {
            @Override public String getId() { return "test"; }
            @Override public String getDisplayName() { return "Test"; }
            @Override public void onPlayerQuit(Game game, GamePlayer player) { quitCallbacks.incrementAndGet(); }
        };
        Arena arena = new Arena("test", "Test Arena", "test_world")
                .withModeSetting("general", "disconnect_grace_seconds", "0");
        GameManager manager = new GameManager();
        Game game = manager.createGame(mode, arena, new GameSettings(1, 2, 0, 300, false, true));
        GamePlayer player = manager.addPlayer(UUID.randomUUID());
        player.setStatus(de.jawollo07.manhunt.player.PlayerStatus.PLAYING);
        player.disconnect(Instant.now().minusSeconds(3600));
        manager.transition(GameState.STARTING);
        manager.transition(GameState.RUNNING);

        new GameLifecycleService(manager).tick(1);

        assertEquals(0, quitCallbacks.get());
        assertTrue(player.isActiveForRound());
        assertFalse(player.isConnected());
    }

    @Test
    void randomTeamDistributionDefaultsToEnabledAndCanBeDisabled() {
        GameSettings defaults = new GameSettings(2, 8, 5, 300, false, true);
        GameSettings ordered = new GameSettings(2, 8, 5, 300, false, true, false);

        assertTrue(defaults.randomTeamDistribution());
        assertFalse(ordered.randomTeamDistribution());
    }

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
