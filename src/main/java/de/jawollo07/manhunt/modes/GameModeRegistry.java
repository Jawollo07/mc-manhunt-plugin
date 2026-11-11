package de.jawollo07.manhunt.modes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GameModeRegistry {
    private final Map<String, GameMode> modes = new LinkedHashMap<>();

    public void register(GameMode mode) {
        Objects.requireNonNull(mode, "mode");
        String id = Objects.requireNonNull(mode.getId(), "mode id").trim().toLowerCase();
        if (id.isEmpty()) throw new IllegalArgumentException("Game mode id must not be blank");
        if (modes.putIfAbsent(id, mode) != null) {
            throw new IllegalArgumentException("Game mode is already registered: " + id);
        }
    }

    public Optional<GameMode> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(modes.get(id.trim().toLowerCase()));
    }

    public Collection<GameMode> getAll() {
        return Collections.unmodifiableCollection(modes.values());
    }

    public boolean unregister(String id) {
        return id != null && modes.remove(id.trim().toLowerCase()) != null;
    }

    public boolean isRegistered(String id) {
        return get(id).isPresent();
    }
}
