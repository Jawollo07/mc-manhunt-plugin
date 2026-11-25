package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.modes.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaValidationTest {
    private GameMode modeWithRequiredSpawn(String key) {
        return new GameMode() {
            @Override public String getId() { return "custom"; }
            @Override public String getDisplayName() { return "Custom"; }
            @Override public List<String> getRequiredArenaSpawnPoints() { return List.of(key); }
        };
    }

    @Test
    void rejectsMissingModeRequiredSpawnPoint() {
        Arena arena = new Arena("forest", "Forest", "forest");
        ArenaValidation result = ArenaValidation.check(arena, modeWithRequiredSpawn("team:hunters"), true, false);

        assertFalse(result.ready());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("team:hunters")));
    }

    @Test
    void acceptsRequiredSpawnAndReportsNaturalSpawnFallbackAsWarning() {
        Arena arena = new Arena("forest", "Forest", "forest")
                .withSpawnPoint(new ArenaSpawnPoint("team:hunters", 0, 64, 0, 0, 0));
        ArenaValidation result = ArenaValidation.check(arena, modeWithRequiredSpawn("team:hunters"), true, false);

        assertTrue(result.ready());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("Weltspawn")));
    }


    @Test
    void acceptsAutomaticallyNumberedTeamSpawnPointsForRequiredTeam() {
        Arena arena = new Arena("forest", "Forest", "forest")
                .withSpawnPoint(new ArenaSpawnPoint("team:hunters:1", 0, 64, 0, 0, 0))
                .withSpawnPoint(new ArenaSpawnPoint("team:hunters:2", 4, 64, 0, 0, 0));
        ArenaValidation result = ArenaValidation.check(arena, modeWithRequiredSpawn("team:hunters"), true, false);

        assertTrue(result.ready());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("Weltspawn")));
    }

    @Test
    void rejectsModesThatAreNotEnabledForArena() {
        Arena arena = new Arena("forest", "Forest", "forest")
                .withEnabledModes(java.util.Set.of("infection"));

        ArenaValidation result = ArenaValidation.check(arena, modeWithRequiredSpawn("default"), true, false);

        assertFalse(result.ready());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("nicht freigegeben")));
    }

    @Test
    void rejectsInvalidOrAlreadyLoadedTemplates() {
        Arena arena = new Arena("forest", "Forest", "forest");
        GameMode mode = modeWithRequiredSpawn("default");

        assertFalse(ArenaValidation.check(arena, mode, false, false).ready());
        assertFalse(ArenaValidation.check(arena, mode, true, true).ready());
    }

    @Test
    void reportsModeSpecificOptionalSpawnRecommendations() {
        Arena arena = new Arena("forest", "Forest", "forest");
        GameMode hitman = new GameMode() {
            @Override public String getId() { return "hitman"; }
            @Override public String getDisplayName() { return "Hitman"; }
        };

        ArenaValidation result = ArenaValidation.check(arena, hitman, true, false);

        assertTrue(result.ready());
        assertFalse(result.warnings().stream().anyMatch(message -> message.contains("team:hitmen")));
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("extraction")));
    }
}
