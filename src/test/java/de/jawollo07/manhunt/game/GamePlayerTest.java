package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.player.PlayerStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GamePlayerTest {
    @Test
    void reconnectRestoresThePreviousStatus() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setStatus(PlayerStatus.ELIMINATED);

        player.disconnect();
        assertFalse(player.isConnected());
        assertTrue(player.isEliminated());

        player.reconnect();
        assertTrue(player.isConnected());
        assertTrue(player.isEliminated());
        assertEquals(PlayerStatus.ELIMINATED, player.getStatus());
    }

    @Test
    void activeParticipantRemainsAliveDuringTemporaryDisconnect() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setStatus(PlayerStatus.PLAYING);

        assertTrue(player.isActiveForRound());
        player.disconnect();

        assertEquals(PlayerStatus.DISCONNECTED, player.getStatus());
        assertTrue(player.isActiveForRound());
        player.reconnect();
        assertTrue(player.isActiveForRound());
        assertEquals(PlayerStatus.PLAYING, player.getStatus());
    }

    @Test
    void eliminatedParticipantDoesNotBecomeActiveDuringDisconnect() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setStatus(PlayerStatus.ELIMINATED);
        player.disconnect();

        assertFalse(player.isActiveForRound());
        assertTrue(player.isEliminated());
        player.reconnect();
        assertFalse(player.isActiveForRound());
        assertEquals(PlayerStatus.ELIMINATED, player.getStatus());
    }

    @Test
    void disconnectGraceExpiresLivingPlayerExactlyOnceAndKeepsReconnectState() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setStatus(PlayerStatus.PLAYING);
        java.time.Instant disconnectedAt = java.time.Instant.parse("2026-01-01T12:00:00Z");
        player.disconnect(disconnectedAt);

        assertFalse(player.hasExceededDisconnectGrace(java.time.Duration.ofSeconds(60),
                disconnectedAt.plusSeconds(59)));
        assertTrue(player.hasExceededDisconnectGrace(java.time.Duration.ofSeconds(60),
                disconnectedAt.plusSeconds(60)));
        assertTrue(player.expireDisconnect());
        assertFalse(player.isActiveForRound());
        assertTrue(player.isEliminated());
        assertFalse(player.isConnected());
        assertFalse(player.expireDisconnect(), "expiry callback must only happen once");

        player.reconnect();
        assertTrue(player.isConnected());
        assertEquals(PlayerStatus.ELIMINATED, player.getStatus());
        assertTrue(player.isEliminated());
    }

    @Test
    void disabledDisconnectGraceNeverExpires() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.setStatus(PlayerStatus.PLAYING);
        player.disconnect(java.time.Instant.parse("2026-01-01T12:00:00Z"));

        assertFalse(player.hasExceededDisconnectGrace(java.time.Duration.ZERO,
                java.time.Instant.parse("2026-01-02T12:00:00Z")));
    }

    @Test
    void reconnectRestoresWaitingStatusForLobbyDisconnect() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.disconnect();
        player.reconnect();

        assertEquals(PlayerStatus.WAITING, player.getStatus());
    }
}
