package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.player.PlayerStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class GamePlayer {
    private final UUID uniqueId;
    private final Instant joinedAt;
    private PlayerStatus status;

    public GamePlayer(UUID uniqueId) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.joinedAt = Instant.now();
        this.status = PlayerStatus.WAITING;
    }

    public UUID getUniqueId() { return uniqueId; }
    public Instant getJoinedAt() { return joinedAt; }
    public PlayerStatus getStatus() { return status; }

    public void setStatus(PlayerStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isEliminated() {
        return status == PlayerStatus.ELIMINATED;
    }

    public boolean isConnected() {
        return status != PlayerStatus.DISCONNECTED;
    }
}
