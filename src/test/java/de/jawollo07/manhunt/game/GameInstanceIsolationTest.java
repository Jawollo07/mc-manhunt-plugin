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
}
