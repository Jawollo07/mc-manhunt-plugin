package de.jawollo07.manhunt.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A mode-defined role assigned to one player for one game session.
 * The core stores the identifier but deliberately does not interpret it.
 */
public record RoleAssignment(
        UUID playerId,
        String roleId,
        UUID teamId,
        Instant assignedAt
) {
    public RoleAssignment {
        Objects.requireNonNull(playerId, "playerId");
        if (roleId == null || roleId.isBlank()) {
            throw new IllegalArgumentException("roleId must not be blank");
        }
        assignedAt = Objects.requireNonNullElseGet(assignedAt, Instant::now);
    }

    public Optional<UUID> optionalTeamId() {
        return Optional.ofNullable(teamId);
    }
}
