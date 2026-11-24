package de.jawollo07.manhunt.stats;

import java.time.Instant;
import java.util.UUID;

public record GameHistoryEntry(
        UUID gameId,
        String modeId,
        String arenaId,
        String outcome,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        String endReason
) {}
