package de.jawollo07.manhunt.modes;

import de.jawollo07.manhunt.game.Game;

import java.util.List;

/**
 * Defines mode-specific rules. Shared game lifecycle and infrastructure stay in the core.
 * Implementations are called from the server's main thread.
 */
public interface GameMode {
    String getId();
    String getDisplayName();

    /** Minimum roster size required by this mode before its countdown may start. */
    default int getMinimumPlayers() { return 2; }

    /** Spawn points this mode cannot run without. Most modes use the arena/world spawn fallback. */
    default List<String> getRequiredArenaSpawnPoints() { return List.of(); }

    default void onPrepare(Game game) {}
    default void onStart(Game game) {}
    default void onEnd(Game game) {}

    /** Called after a participant has joined the game roster. */
    default void onPlayerJoin(Game game, de.jawollo07.manhunt.game.GamePlayer player) {}

    /** Called after a participant has left or disconnected. */
    default void onPlayerQuit(Game game, de.jawollo07.manhunt.game.GamePlayer player) {}

    /** Called when a participant dies; killerId is null for environmental deaths. */
    default void onPlayerDeath(Game game, de.jawollo07.manhunt.game.GamePlayer player, java.util.UUID killerId) {}

    /** Called by the core's scheduled game ticker while the game is running. */
    default void onTick(Game game, long elapsedSeconds) {}
}
