package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameInstanceIsolationTest {
    private Game newGame() {
        GameMode mode = new GameMode() {
            @Override public String getId() { return "test"; }
            @Override public String getDisplayName() { return "Test"; }
        };
        return new Game(UUID.randomUUID(), mode,
                new Arena("test", "Test", "test_world"),
                new GameSettings(1, 8, 10, 600, false, true));
    }

    @Test
    void rolesAndTeamsAreIsolatedBetweenGameInstances() {
        Game first = newGame();
        Game second = newGame();
        UUID player = UUID.randomUUID();

        first.getRoleManager().assign(player, "hunter");
        var team = first.getTeamManager().createTeam("Hunters", "RED", 4, false);
        first.getTeamManager().assignPlayer(player, team.getId());

        assertTrue(first.getRoleManager().hasAssignment(player));
        assertFalse(second.getRoleManager().hasAssignment(player));
        assertTrue(first.getTeamManager().getTeamOf(player).isPresent());
        assertTrue(second.getTeamManager().getTeamOf(player).isEmpty());
    }

    @Test
    void removingParticipantAlsoRemovesTheirRoleAndTeamMembership() {
        Game game = newGame();
        UUID player = UUID.randomUUID();
        game.addPlayer(new GamePlayer(player));
        var team = game.getTeamManager().createTeam("Runners", "GREEN", 4, false);
        game.getTeamManager().assignPlayer(player, team.getId());
        game.getRoleManager().assign(player, "runner");

        assertTrue(game.removePlayer(player).isPresent());
        assertFalse(game.getRoleManager().hasAssignment(player));
        assertTrue(game.getTeamManager().getTeamOf(player).isEmpty());
        assertTrue(game.removePlayer(player).isEmpty());
    }
    @Test
    void tracksLobbyReadinessOnlyForRegisteredPlayers() {
        Game game = newGame();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        game.addPlayer(new GamePlayer(first));
        game.addPlayer(new GamePlayer(second));

        assertFalse(game.areAllPlayersReady(id -> true));
        assertTrue(game.toggleReady(first));
        assertFalse(game.areAllPlayersReady(id -> true));
        assertTrue(game.toggleReady(second));
        assertTrue(game.areAllPlayersReady(id -> true));
        assertFalse(game.toggleReady(first));
        assertFalse(game.areAllPlayersReady(id -> true));
        assertThrows(IllegalArgumentException.class, () -> game.toggleReady(UUID.randomUUID()));
    }

    @Test
    void storesAndCountsArenaVotesAndRemovesVotesWhenPlayerLeaves() {
        Game game = newGame();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        game.addPlayer(new GamePlayer(first));
        game.addPlayer(new GamePlayer(second));

        game.castArenaVote(first, "desert");
        game.castArenaVote(second, "forest");
        assertEquals(1L, game.getArenaVoteCounts().get("desert"));
        assertEquals(1L, game.getArenaVoteCounts().get("forest"));
        assertEquals("desert", game.getWinningArenaVote().orElseThrow());

        game.castArenaVote(second, "desert");
        assertEquals("desert", game.getWinningArenaVote().orElseThrow());
        game.removePlayer(first);
        assertEquals(1L, game.getArenaVoteCounts().get("desert"));
    }

    @Test
    void invitationCanOnlyBeAcceptedByTheInvitedPlayerAndIsConsumedOnce() {
        Game game = newGame();
        UUID inviter = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        game.addPlayer(new GamePlayer(inviter));

        game.invitePlayer(inviter, invited);
        assertTrue(game.consumeInvitation(invited));
        assertFalse(game.consumeInvitation(invited));
        assertThrows(IllegalArgumentException.class, () -> game.invitePlayer(UUID.randomUUID(), invited));
    }

}
