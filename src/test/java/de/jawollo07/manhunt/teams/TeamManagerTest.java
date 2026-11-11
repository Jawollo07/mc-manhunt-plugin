package de.jawollo07.manhunt.teams;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamManagerTest {
    @Test
    void playerCanOnlyBelongToOneTeam() {
        TeamManager manager = new TeamManager();
        Team hunters = manager.createTeam("Hunters", "red", 4, false);
        Team runners = manager.createTeam("Runners", "green", 4, false);
        UUID playerId = UUID.randomUUID();

        manager.assignPlayer(playerId, hunters.getId());
        manager.assignPlayer(playerId, runners.getId());

        assertFalse(hunters.hasMember(playerId));
        assertTrue(runners.hasMember(playerId));
        assertEquals(runners, manager.getTeamOf(playerId).orElseThrow());
    }

    @Test
    void fullTeamDoesNotRemovePlayerFromCurrentTeam() {
        TeamManager manager = new TeamManager();
        Team source = manager.createTeam("Source", "blue", 2, false);
        Team full = manager.createTeam("Full", "red", 1, false);
        UUID movingPlayer = UUID.randomUUID();

        manager.assignPlayer(movingPlayer, source.getId());
        manager.assignPlayer(UUID.randomUUID(), full.getId());

        assertThrows(IllegalStateException.class,
                () -> manager.assignPlayer(movingPlayer, full.getId()));
        assertTrue(source.hasMember(movingPlayer));
    }

    @Test
    void deletingTeamRemovesAllMembershipReferences() {
        TeamManager manager = new TeamManager();
        Team team = manager.createTeam("Hunters", "red", 4, false);
        UUID playerId = UUID.randomUUID();
        manager.assignPlayer(playerId, team.getId());

        manager.deleteTeam(team.getId());

        assertTrue(manager.getTeamOf(playerId).isEmpty());
        assertTrue(manager.getTeams().isEmpty());
    }
}
