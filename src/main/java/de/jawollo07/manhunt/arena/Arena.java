package de.jawollo07.manhunt.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints, java.util.Set<String> enabledModeIds, Map<String, Map<String, String>> modeSettings) {
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
        Objects.requireNonNull(modeSettings, "modeSettings");
        Map<String, Map<String, String>> settingsCopy = new LinkedHashMap<>();
        modeSettings.forEach((modeId, values) -> {
            if (modeId == null || !modeId.trim().matches("[a-zA-Z0-9_-]{1,64}"))
                throw new IllegalArgumentException("Invalid mode settings id: " + modeId);
            Map<String, String> normalized = new LinkedHashMap<>();
            Objects.requireNonNull(values, "mode setting values").forEach((key, value) -> {
                if (key == null || !key.matches("[a-zA-Z0-9_-]{1,64}") || value == null || value.length() > 128)
                    throw new IllegalArgumentException("Invalid mode setting: " + key);
                normalized.put(key.toLowerCase(java.util.Locale.ROOT), value);
            });
            settingsCopy.put(modeId.toLowerCase(java.util.Locale.ROOT), Collections.unmodifiableMap(normalized));
        });
        modeSettings = Collections.unmodifiableMap(settingsCopy);
    }

    /** An empty mode set keeps legacy arenas compatible with every registered mode. */
    public Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints,
                 java.util.Set<String> enabledModeIds) {
        this(id, displayName, worldName, spawnPoints, enabledModeIds, Map.of());
    }

    public Arena(String id, String displayName, String worldName, Map<String, ArenaSpawnPoint> spawnPoints) {
        this(id, displayName, worldName, spawnPoints, java.util.Set.of(), Map.of());
    }

    public Arena(String id, String displayName, String worldName) {
        this(id, displayName, worldName, Map.of(), java.util.Set.of(), Map.of());
    }

    public boolean supportsMode(String modeId) {
        return enabledModeIds.isEmpty() || enabledModeIds.contains(modeId.toLowerCase(java.util.Locale.ROOT));
    }

    public Arena withEnabledModes(java.util.Set<String> modeIds) {
        return new Arena(id, displayName, worldName, spawnPoints, modeIds, modeSettings);
    }

    public String modeSetting(String modeId, String key, String defaultValue) {
        return modeSettings.getOrDefault(modeId.toLowerCase(java.util.Locale.ROOT), Map.of())
                .getOrDefault(key.toLowerCase(java.util.Locale.ROOT), defaultValue);
    }

    public int modeIntSetting(String modeId, String key, int defaultValue) {
        try { return Integer.parseInt(modeSetting(modeId, key, Integer.toString(defaultValue))); }
        catch (NumberFormatException ignored) { return defaultValue; }
    }

    public boolean modeBooleanSetting(String modeId, String key, boolean defaultValue) {
        return Boolean.parseBoolean(modeSetting(modeId, key, Boolean.toString(defaultValue)));
    }

    public Arena withModeSetting(String modeId, String key, String value) {
        Map<String, Map<String, String>> updated = new LinkedHashMap<>(modeSettings);
        Map<String, String> values = new LinkedHashMap<>(updated.getOrDefault(modeId.toLowerCase(java.util.Locale.ROOT), Map.of()));
        values.put(key.toLowerCase(java.util.Locale.ROOT), Objects.requireNonNull(value, "value"));
        updated.put(modeId.toLowerCase(java.util.Locale.ROOT), values);
        return new Arena(id, displayName, worldName, spawnPoints, enabledModeIds, updated);
    }

    public Optional<ArenaSpawnPoint> spawnPoint(String name) {
        return Optional.ofNullable(spawnPoints.get(normalizeSpawnKey(name)));
    }

    public Arena withSpawnPoint(ArenaSpawnPoint point) {
        Objects.requireNonNull(point, "point");
        Map<String, ArenaSpawnPoint> updated = new LinkedHashMap<>(spawnPoints);
        updated.put(normalizeSpawnKey(point.name()), point);
        return new Arena(id, displayName, worldName, updated, enabledModeIds, modeSettings);
    }

    public Arena withoutSpawnPoint(String name) {
        Map<String, ArenaSpawnPoint> updated = new LinkedHashMap<>(spawnPoints);
        updated.remove(normalizeSpawnKey(name));
        return new Arena(id, displayName, worldName, updated);
    }

    /** Creates an independent arena entry using the same template and spawn layout. */
    public Arena copyAs(String newId, String newDisplayName) {
        return new Arena(newId, newDisplayName, worldName, spawnPoints, enabledModeIds, modeSettings);
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
