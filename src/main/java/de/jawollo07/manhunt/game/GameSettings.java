package de.jawollo07.manhunt.game;

public record GameSettings(
        int minPlayers,
        int maxPlayers,
        int countdownSeconds,
        int timeLimitSeconds,
        boolean allowLateJoin,
        boolean allowSpectators
) {
    public GameSettings {
        if (minPlayers < 1) {
            throw new IllegalArgumentException("minPlayers must be positive");
        }

        if (maxPlayers < minPlayers) {
            throw new IllegalArgumentException("maxPlayers must be >= minPlayers");
        }

        if (countdownSeconds < 0 || timeLimitSeconds < 0) {
            throw new IllegalArgumentException("Invalid time settings");
        }
    }
}