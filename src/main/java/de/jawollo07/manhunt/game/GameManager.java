package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameMode;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GameManager {
    private Game activeGame;

    public Game createGame(GameMode mode, Arena arena, GameSettings settings) {
        if (activeGame != null && !activeGame.isTerminal()) {
            throw new IllegalStateException("A game is already active");
        }

        Objects.requireNonNull(mode, "mode");
        if (mode.getId() == null || mode.getId().isBlank()) {
            throw new IllegalArgumentException("Game mode id must not be blank");
        }

        activeGame = new Game(UUID.randomUUID(), mode, arena, settings);
        return activeGame;
    }

    public Optional<Game> getActiveGame() {
        return Optional.ofNullable(activeGame);
    }

    public boolean hasActiveGame() {
        return activeGame != null && !activeGame.isTerminal();
    }

    public void transition(GameState nextState) {
        Game game = requireActiveGame();
        game.transitionTo(nextState);
    }

    public void finish(GameResult result) {
        Game game = requireActiveGame();
        if (game.getState() != GameState.ENDING) {
            game.transitionTo(GameState.ENDING);
        }
        if (!game.getId().equals(result.gameId())) {
            throw new IllegalArgumentException("Result belongs to a different game");
        }
        game.setResult(result);
    }

    public void beginReset() {
        Game game = requireActiveGame();
        if (game.getState() != GameState.ENDING) {
            throw new IllegalStateException("Only an ending game can be reset");
        }
        game.transitionTo(GameState.RESETTING);
    }

    public void clearFinishedGame() {
        Game game = requireActiveGame();
        if (game.getState() != GameState.RESETTING) {
            throw new IllegalStateException("Game must be reset before it can be removed");
        }
        activeGame = null;
    }

    private Game requireActiveGame() {
        if (activeGame == null) {
            throw new IllegalStateException("There is no active game");
        }
        return activeGame;
    }
}
