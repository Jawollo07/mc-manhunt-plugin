package de.jawollo07.manhunt.stats;

import java.util.UUID;

public record LeaderboardEntry(
        int rank,
        UUID playerId,
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
