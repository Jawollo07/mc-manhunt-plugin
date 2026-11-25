package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.modes.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mode-aware arena checks. Optional spawn points improve layouts but never block casual rounds. */
public record ArenaValidation(List<String> errors, List<String> warnings) {
    private static final Map<String, List<String>> RECOMMENDED = Map.of(
            "assassin", List.of("team:assassins"),
            "hitman", List.of("team:hitmen", "team:survivors", "extraction"),
            "juggernaut", List.of("team:juggernaut", "team:hunters"),
            "infection", List.of("team:infected", "team:survivors")
    );

    public ArenaValidation {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean ready() {
        return errors.isEmpty();
    }

    public static ArenaValidation check(Arena arena, GameMode mode, boolean templateValid, boolean templateLoaded) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(mode, "mode");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!templateValid) errors.add("Die Kartenvorlage ist ungültig oder unvollständig.");
        if (templateLoaded) errors.add("Die Kartenvorlage ist noch geladen und kann nicht kopiert werden.");

        for (String required : mode.getRequiredArenaSpawnPoints()) {
            if (arena.spawnPoint(required).isEmpty()) {
                errors.add("Pflicht-Spawnpunkt fehlt: " + required);
            }
        }

        if (arena.spawnPoint("default").isEmpty()) {
            warnings.add("Kein Standard-Spawnpunkt gesetzt – der natürliche Weltspawn wird verwendet.");
        }

        for (String recommended : recommendedFor(mode.getId())) {
            if (arena.spawnPoint(recommended).isEmpty()) {
                warnings.add("Optionaler Spawnpunkt fehlt: " + recommended);
            }
        }

        return new ArenaValidation(errors, warnings);
    }

    private static List<String> recommendedFor(String modeId) {
        return RECOMMENDED.getOrDefault(modeId.toLowerCase(java.util.Locale.ROOT), List.of());
    }
}
