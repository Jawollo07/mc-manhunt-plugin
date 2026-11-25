package de.jawollo07.manhunt.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints, java.util.Set<String> enabledModeIds) {
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
        Objects.requireNonNull(enabledModeIds, "enabledModeIds");
        java.util.Set<String> modes = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String modeId : enabledModeIds) {
            if (modeId == null || !modeId.trim().matches("[a-zA-Z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Invalid enabled game mode id: " + modeId);
            }
            modes.add(modeId.trim().toLowerCase(java.util.Locale.ROOT));
        }
        enabledModeIds = Collections.unmodifiableSet(modes);
    }

    /** An empty mode set keeps legacy arenas compatible with every registered mode. */
    public Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints) {
        this(id, displayName, worldName, spawnPoints, java.util.Set.of());
    }

    public Arena(String id, String displayName, String worldName) {
        this(id, displayName, worldName, Map.of(), java.util.Set.of());
    }

    public boolean supportsMode(String modeId) {
        return enabledModeIds.isEmpty() || enabledModeIds.contains(modeId.toLowerCase(java.util.Locale.ROOT));
    }

    public Arena withEnabledModes(java.util.Set<String> modeIds) {
        return new Arena(id, displayName, worldName, spawnPoints, modeIds);
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

    /** Creates an independent arena entry using the same template and spawn layout. */
    public Arena copyAs(String newId, String newDisplayName) {
        return new Arena(newId, newDisplayName, worldName, spawnPoints, enabledModeIds);
    }

    public static String normalizeSpawnKey(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Spawn point name must not be blank");
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+(?::[a-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Invalid spawn point name: " + name);
        }
        return normalized;
    }
}
