package de.jawollo07.manhunt.modes.builtin;

import de.jawollo07.manhunt.game.GamePlayer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TeamDistributionTest {
    @Test
    void juggernautAlwaysGetsOneBossAndAllRemainingPlayersBecomeHunters() {
        List<GamePlayer> players = players(8);
        List<GamePlayer> ordered = order(players, Map.of(), "juggernaut", "hunters", 1);

        assertEquals(8, ordered.size());
        assertEquals(1, ordered.subList(0, 1).size());
        assertEquals(7, ordered.subList(1, ordered.size()).size());
        assertContainsEveryPlayerOnce(players, ordered);
    }

    @Test
    void hitmanSevenPlayerRoundUsesTwoHitmenAndFiveSurvivors() {
        List<GamePlayer> players = players(7);
        List<GamePlayer> ordered = order(players, Map.of(), "hitmen", "survivors", 2);

        assertEquals(2, ordered.subList(0, 2).size());
        assertEquals(5, ordered.subList(2, 7).size());
        assertContainsEveryPlayerOnce(players, ordered);
    }

    @Test
    void infectionSevenPlayerRoundCanStartWithTwoInfectedAndFiveSurvivors() {
        List<GamePlayer> players = players(7);
        List<GamePlayer> ordered = order(players, Map.of(), "infected", "survivors", 2);

        assertEquals(2, ordered.subList(0, 2).size());
        assertEquals(5, ordered.subList(2, 7).size());
        assertContainsEveryPlayerOnce(players, ordered);
    }

    @Test
    void manualPreferencesAreHonoredWhenBothTeamsHaveCapacity() {
        List<GamePlayer> players = players(6);
        Map<UUID, String> preferences = new HashMap<>();
        preferences.put(players.get(0).getUniqueId(), "survivors");
        preferences.put(players.get(1).getUniqueId(), "hitmen");
        preferences.put(players.get(2).getUniqueId(), "survivors");

        List<GamePlayer> ordered = order(players, preferences, "hitmen", "survivors", 2);

        assertTrue(ordered.subList(0, 2).stream()
                .noneMatch(player -> preferences.getOrDefault(player.getUniqueId(), "").equals("survivors")));
        assertTrue(ordered.subList(2, 6).contains(players.get(0)));
        assertTrue(ordered.subList(2, 6).contains(players.get(2)));
        assertTrue(ordered.subList(0, 2).contains(players.get(1)));
        assertContainsEveryPlayerOnce(players, ordered);
    }

    @Test
    void oversubscribedTeamWishesFallBackWithoutDroppingOrDuplicatingPlayers() {
        List<GamePlayer> players = players(7);
        Map<UUID, String> preferences = players.stream()
                .collect(Collectors.toMap(GamePlayer::getUniqueId, ignored -> "juggernaut"));

        List<GamePlayer> ordered = order(players, preferences, "juggernaut", "hunters", 1);

        assertEquals(1, ordered.subList(0, 1).size());
        assertEquals(6, ordered.subList(1, 7).size());
        assertContainsEveryPlayerOnce(players, ordered);
    }

    @Test
    void randomDistributionStillPreservesTeamCapacitiesAndAllPlayers() {
        List<GamePlayer> players = players(8);
        for (int attempt = 0; attempt < 20; attempt++) {
            List<GamePlayer> ordered = TeamDistribution.order(players, ignored -> "", true,
                    "juggernaut", "hunters", 1);
            assertEquals(1, ordered.subList(0, 1).size());
            assertEquals(7, ordered.subList(1, 8).size());
            assertContainsEveryPlayerOnce(players, ordered);
        }
    }

    @Test
    void invalidPrimaryCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TeamDistribution.order(players(2), ignored -> "", false,
                        "a", "b", 3));
    }

    private static List<GamePlayer> players(int count) {
        List<GamePlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(new GamePlayer(UUID.randomUUID()));
        return players;
    }

    private static List<GamePlayer> order(List<GamePlayer> players, Map<UUID, String> preferences,
                                          String primary, String secondary, int capacity) {
        Function<GamePlayer, String> preference = player ->
                preferences.getOrDefault(player.getUniqueId(), "");
        return TeamDistribution.order(players, preference, false, primary, secondary, capacity);
    }

    private static void assertContainsEveryPlayerOnce(List<GamePlayer> expected, List<GamePlayer> actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.stream().map(GamePlayer::getUniqueId).collect(Collectors.toSet()),
                actual.stream().map(GamePlayer::getUniqueId).collect(Collectors.toSet()));
        assertEquals(actual.size(), actual.stream().map(GamePlayer::getUniqueId).distinct().count());
    }
}
