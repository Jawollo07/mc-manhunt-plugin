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
    private java.time.Instant disconnectedAt;
    private boolean disconnectExpired;

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
                disconnectedAt = java.time.Instant.now();
                disconnectExpired = false;
            }
            this.status = PlayerStatus.DISCONNECTED;
            return;
        }
        this.status = status;
        statusBeforeDisconnect = null;
        disconnectedAt = null;
        disconnectExpired = false;
    }

    public void disconnect() {
        disconnect(java.time.Instant.now());
    }

    public void disconnect(java.time.Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == PlayerStatus.DISCONNECTED) return;
        statusBeforeDisconnect = status;
        status = PlayerStatus.DISCONNECTED;
        disconnectedAt = at;
        disconnectExpired = false;
    }

    public void reconnect() {
        if (status != PlayerStatus.DISCONNECTED) return;
        status = statusBeforeDisconnect == null ? PlayerStatus.WAITING : statusBeforeDisconnect;
        statusBeforeDisconnect = null;
        disconnectedAt = null;
        disconnectExpired = false;
    }

    public java.util.Optional<java.time.Instant> getDisconnectedAt() {
        return java.util.Optional.ofNullable(disconnectedAt);
    }

    public boolean hasExceededDisconnectGrace(java.time.Duration grace, java.time.Instant now) {
        Objects.requireNonNull(grace, "grace");
        Objects.requireNonNull(now, "now");
        return status == PlayerStatus.DISCONNECTED && !disconnectExpired
                && disconnectedAt != null && !now.isBefore(disconnectedAt.plus(grace));
    }

    /**
     * Marks a disconnected living participant as eliminated exactly once.
     * The DISCONNECTED state is retained until login so reconnect placement
     * still runs and returns the player as a spectator.
     */
    public boolean expireDisconnect() {
        if (status != PlayerStatus.DISCONNECTED || disconnectExpired) return false;
        disconnectExpired = true;
        if (statusBeforeDisconnect != PlayerStatus.PLAYING) return false;
        statusBeforeDisconnect = PlayerStatus.ELIMINATED;
        return true;
    }

    public boolean isEliminated() {
        return status == PlayerStatus.ELIMINATED
                || (status == PlayerStatus.DISCONNECTED && statusBeforeDisconnect == PlayerStatus.ELIMINATED);
    }

    public boolean isConnected() {
        return status != PlayerStatus.DISCONNECTED;
    }

    /**
     * Whether this participant is still alive in the current round. A short
     * disconnect does not count as an elimination; the saved status decides.
     */
    public boolean isActiveForRound() {
        return status == PlayerStatus.PLAYING
                || (status == PlayerStatus.DISCONNECTED && statusBeforeDisconnect == PlayerStatus.PLAYING);
    }
}
