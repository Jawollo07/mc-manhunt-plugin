package de.jawollo07.manhunt.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints) {
    public Arena {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Arena id must not be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Arena display name must not be blank");
        if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("World name must not be blank");
        Objects.requireNonNull(spawnPoints, "spawnPoints");
        Map<String, ArenaSpawnPoint> copy = new LinkedHashMap<>();
        spawnPoints.forEach((key, point) -> {
            String normalized = normalizeSpawnKey(key);
            Objects.requireNonNull(point, "spawn point");
            if (!normalized.equals(normalizeSpawnKey(point.name()))) {
                throw new IllegalArgumentException("Spawn point map key must match its name: " + key);
            }
            if (copy.putIfAbsent(normalized, point) != null) {
                throw new IllegalArgumentException("Duplicate spawn point: " + normalized);
            }
        });
        spawnPoints = Collections.unmodifiableMap(copy);
    }

    public Arena(String id, String displayName, String worldName) {
        this(id, displayName, worldName, Map.of());
    }

    public Optional<ArenaSpawnPoint> spawnPoint(String name) {
        return Optional.ofNullable(spawnPoints.get(normalizeSpawnKey(name)));
    }

    public Arena withSpawnPoint(ArenaSpawnPoint point) {
        Objects.requireNonNull(point, "point");
        Map<String, ArenaSpawnPoint> updated = new LinkedHashMap<>(spawnPoints);
        updated.put(normalizeSpawnKey(point.name()), point);
        return new Arena(id, displayName, worldName, updated);
    }

    public Arena withoutSpawnPoint(String name) {
        Map<String, ArenaSpawnPoint> updated = new LinkedHashMap<>(spawnPoints);
        updated.remove(normalizeSpawnKey(name));
        return new Arena(id, displayName, worldName, updated);
    }

    public static String normalizeSpawnKey(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Spawn point name must not be blank");
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+(?::[a-z0-9_-]+)?")) {
            throw new IllegalArgumentException("Invalid spawn point name: " + name);
        }
        return normalized;
    }
}
