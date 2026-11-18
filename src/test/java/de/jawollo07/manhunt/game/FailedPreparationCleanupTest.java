package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FailedPreparationCleanupTest {
    @Test
    void clearsPartialModeAssignmentsWhenPreparationFails() {
        GameMode mode = new GameMode() {
            @Override public String getId() { return "failing"; }
            @Override public String getDisplayName() { return "Failing"; }
            @Override public void onPrepare(Game game) {
                var team = game.getTeamManager().createTeam("Partial", "RED", 4, false);
                UUID playerId = game.getPlayers().iterator().next().getUniqueId();
                game.getTeamManager().assignPlayer(playerId, team.getId());
                game.getRoleManager().assign(playerId, "partial", team.getId());
                throw new IllegalStateException("simulated preparation failure");
            }
        };

        GameManager manager = new GameManager();
        Game game = manager.createGame(mode, new Arena("arena", "Arena", "template"),
                new GameSettings(2, 8, 0, 300, false, true));
        manager.transition(GameState.LOBBY);
        manager.addPlayer(UUID.randomUUID());
        manager.addPlayer(UUID.randomUUID());

        GameLifecycleService lifecycle = new GameLifecycleService(manager);
        assertThrows(IllegalStateException.class, lifecycle::prepare);
        assertTrue(game.getTeamManager().getTeams().isEmpty());
        assertEquals(0, game.getRoleManager().size());
        assertEquals(GameState.LOBBY, game.getState());
    }
}
