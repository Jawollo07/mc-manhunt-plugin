package de.jawollo07.manhunt.game;

import java.util.Objects;

/**
 * Coordinates mode callbacks with the shared game state machine.
 * Must only be called from the Paper server thread.
 */
public final class GameLifecycleService {
    private final GameManager gameManager;

    public GameLifecycleService(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    public void prepare() {
        Game game = requireGame();
        if (game.getState() != GameState.WAITING
                && game.getState() != GameState.LOBBY
                && game.getState() != GameState.COUNTDOWN) {
            throw new IllegalStateException("Game cannot be prepared from state " + game.getState());
        }
        game.getMode().onPrepare(game);
        gameManager.transition(GameState.STARTING);
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
        game.getMode().onEnd(game);
    }

    public void beginReset() {
        gameManager.beginReset();
    }

    /**
     * Call only after players have been restored and the temporary map has
     * been unloaded and cleaned up.
     */
    public void completeReset() {
        gameManager.clearFinishedGame();
    }

    private Game requireGame() {
        return gameManager.getActiveGame().orElseThrow(
                () -> new IllegalStateException("There is no active game")
        );
    }
}
