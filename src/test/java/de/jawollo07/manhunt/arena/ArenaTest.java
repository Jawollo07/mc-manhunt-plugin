package de.jawollo07.manhunt.arena;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArenaTest {
    @Test
    void storesAndNormalizesNamedSpawnPoints() {
        Arena arena = new Arena("forest", "Forest", "manhunt_forest")
                .withSpawnPoint(new ArenaSpawnPoint("default", 1.5, 64, -2.5, 90, 0))
                .withSpawnPoint(new ArenaSpawnPoint("team:hunter", 10, 70, 10, 180, 0));

        assertEquals(1.5, arena.spawnPoint("DEFAULT").orElseThrow().x());
        assertEquals(10, arena.spawnPoint("team:HUNTER").orElseThrow().x());
        assertEquals(2, arena.spawnPoints().size());
    }

    @Test
    void doesNotMutateOriginalArenaWhenChangingSpawnPoints() {
        Arena original = new Arena("forest", "Forest", "manhunt_forest");
        Arena updated = original.withSpawnPoint(new ArenaSpawnPoint("default", 0, 64, 0, 0, 0));

        assertTrue(original.spawnPoints().isEmpty());
        assertEquals(1, updated.spawnPoints().size());
        assertEquals(0, updated.withoutSpawnPoint("default").spawnPoints().size());
    }

    @Test
    void rejectsInvalidSpawnPointKeysAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new Arena("forest", "Forest", "map")
                        .withSpawnPoint(new ArenaSpawnPoint("team:bad name", 0, 64, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaSpawnPoint("default", Double.NaN, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Arena("forest", "Forest", "map",
                        Map.of("different", new ArenaSpawnPoint("default", 0, 64, 0, 0, 0))));
    }
}
