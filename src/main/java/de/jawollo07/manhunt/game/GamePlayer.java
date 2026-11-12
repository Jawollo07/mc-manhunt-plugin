package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.player.PlayerStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class GamePlayer {
    private final UUID uniqueId;
    private final Instant joinedAt;
    private PlayerStatus status;
    private PlayerStatus statusBeforeDisconnect;

    public GamePlayer(UUID uniqueId) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.joinedAt = Instant.now();
        this.status = PlayerStatus.WAITING;
    }

    public UUID getUniqueId() { return uniqueId; }
    public Instant getJoinedAt() { return joinedAt; }
    public PlayerStatus getStatus() { return status; }

    /**
     * Updates the logical status while preserving the pre-disconnect status.
     * This allows an eliminated player to reconnect without becoming active again.
     */
    public void setStatus(PlayerStatus status) {
        Objects.requireNonNull(status, "status");
        if (status == PlayerStatus.DISCONNECTED) {
            if (this.status != PlayerStatus.DISCONNECTED) {
                statusBeforeDisconnect = this.status;
            }
            this.status = PlayerStatus.DISCONNECTED;
            return;
        }
        this.status = status;
        statusBeforeDisconnect = null;
    }

    public void disconnect() {
        setStatus(PlayerStatus.DISCONNECTED);
    }

    public void reconnect() {
        if (status != PlayerStatus.DISCONNECTED) return;
        status = statusBeforeDisconnect == null ? PlayerStatus.WAITING : statusBeforeDisconnect;
        statusBeforeDisconnect = null;
    }

    public boolean isEliminated() {
        return status == PlayerStatus.ELIMINATED
                || (status == PlayerStatus.DISCONNECTED && statusBeforeDisconnect == PlayerStatus.ELIMINATED);
    }

    public boolean isConnected() {
        return status != PlayerStatus.DISCONNECTED;
    }
}
