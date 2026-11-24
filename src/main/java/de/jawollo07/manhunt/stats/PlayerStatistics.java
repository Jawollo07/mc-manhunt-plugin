package de.jawollo07.manhunt.stats;

import java.util.UUID;

public record PlayerStatistics(
        UUID playerId,
        String modeId,
        long gamesPlayed,
        long wins,
        long losses,
        long draws,
        long totalSeconds
) {
    public double winRate() {
        return gamesPlayed == 0 ? 0.0 : (double) wins * 100.0 / gamesPlayed;
    }
}
