package de.jawollo07.manhunt.modes.builtin;

import de.jawollo07.manhunt.game.GamePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Pure team-ordering logic shared by the team-based built-in modes. */
final class TeamDistribution {
    private TeamDistribution() {}

    static List<GamePlayer> order(List<GamePlayer> source,
                                  Function<GamePlayer, String> preferenceOf,
                                  boolean randomize,
                                  String primaryKey,
                                  String secondaryKey,
                                  int primaryCapacity) {
        if (source == null || preferenceOf == null || primaryKey == null || secondaryKey == null) {
            throw new IllegalArgumentException("Team distribution arguments must not be null");
        }
        if (primaryCapacity < 0 || primaryCapacity > source.size()) {
            throw new IllegalArgumentException("Primary team capacity is outside the player count");
        }

        List<GamePlayer> shuffled = new ArrayList<>(source);
        if (randomize) Collections.shuffle(shuffled);
        String primary = primaryKey.toLowerCase(Locale.ROOT);
        String secondary = secondaryKey.toLowerCase(Locale.ROOT);
        List<GamePlayer> primaryWishes = new ArrayList<>();
        List<GamePlayer> secondaryWishes = new ArrayList<>();
        List<GamePlayer> neutral = new ArrayList<>();
        for (GamePlayer player : shuffled) {
            String preference = preferenceOf.apply(player);
            if (preference == null) preference = "";
            preference = preference.toLowerCase(Locale.ROOT);
            if (preference.equals(primary)) primaryWishes.add(player);
            else if (preference.equals(secondary)) secondaryWishes.add(player);
            else neutral.add(player);
        }

        int secondaryCapacity = shuffled.size() - primaryCapacity;
        List<GamePlayer> primaryTeam = take(primaryWishes, primaryCapacity);
        List<GamePlayer> secondaryTeam = take(secondaryWishes, secondaryCapacity);
        List<GamePlayer> overflow = new ArrayList<>();
        overflow.addAll(primaryWishes.subList(primaryTeam.size(), primaryWishes.size()));
        overflow.addAll(secondaryWishes.subList(secondaryTeam.size(), secondaryWishes.size()));

        fill(primaryTeam, neutral, primaryCapacity);
        fill(secondaryTeam, neutral, secondaryCapacity);
        fill(primaryTeam, overflow, primaryCapacity);
        fill(secondaryTeam, overflow, secondaryCapacity);

        List<GamePlayer> ordered = new ArrayList<>(shuffled.size());
        ordered.addAll(primaryTeam);
        ordered.addAll(secondaryTeam);
        return List.copyOf(ordered);
    }

    private static List<GamePlayer> take(List<GamePlayer> source, int capacity) {
        int count = Math.min(source.size(), Math.max(0, capacity));
        return new ArrayList<>(source.subList(0, count));
    }

    private static void fill(List<GamePlayer> target, List<GamePlayer> source, int capacity) {
        while (target.size() < capacity && !source.isEmpty()) {
            target.add(source.remove(0));
        }
    }
}
