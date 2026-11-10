package de.jawollo07.manhunt.game;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameResult(
        UUID gameId,
        GameOutcome outcome,
        List<UUID> winners,
        List<UUID> participants,
        Instant startedAt,
        Instant endedAt,
        Duration duration,
        String endReason
) {
    public GameResult {
        winners = List.copyOf(winners);
        participants = List.copyOf(participants);
    }
}