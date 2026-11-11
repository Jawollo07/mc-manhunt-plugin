package de.jawollo07.manhunt.modes;

/**
 * Describes a mode and creates fresh per-game mode sessions.
 * Mutable round-specific state must live in the returned session, never in
 * the registry's shared factory.
 */
public interface GameModeFactory {
    String getId();
    String getDisplayName();
    GameMode createSession();
}
