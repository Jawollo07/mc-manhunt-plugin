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
    void reconnectRestoresWaitingStatusForLobbyDisconnect() {
        GamePlayer player = new GamePlayer(UUID.randomUUID());
        player.disconnect();
        player.reconnect();

        assertEquals(PlayerStatus.WAITING, player.getStatus());
    }
}
