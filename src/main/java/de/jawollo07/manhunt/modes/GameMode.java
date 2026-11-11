package de.jawollo07.manhunt.modes;

import de.jawollo07.manhunt.game.Game;

/**
 * Defines mode-specific rules. Shared game lifecycle and infrastructure stay in the core.
 * Implementations are called from the server's main thread.
 */
public interface GameMode {
    String getId();
    String getDisplayName();

    default void onPrepare(Game game) {}
    default void onStart(Game game) {}
    default void onEnd(Game game) {}
}
