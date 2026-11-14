package de.jawollo07.manhunt.modes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GameModeRegistry {
    private final Map<String, GameModeFactory> factories = new LinkedHashMap<>();

    public void register(GameModeFactory factory) {
        Objects.requireNonNull(factory, "factory");
        String id = normalize(factory.getId());
        if (factories.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Game mode is already registered: " + id);
        }
    }

    public Optional<GameModeFactory> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(factories.get(normalize(id)));
    }

    public Optional<GameMode> createSession(String id) {
        return get(id).map(factory -> {
            GameMode session = Objects.requireNonNull(
                    factory.createSession(), "Game mode factory returned null"
            );
            if (!normalize(factory.getId()).equals(normalize(session.getId()))) {
                throw new IllegalStateException("Game mode session id does not match its factory");
            }
            return session;
        });
    }

    public Collection<GameModeFactory> getAll() {
        return Collections.unmodifiableCollection(factories.values());
    }

    public boolean unregister(String id) {
        return id != null && factories.remove(normalize(id)) != null;
    }

    public boolean isRegistered(String id) {
        return get(id).isPresent();
    }

    private String normalize(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Game mode id must not be blank");
        return normalized;
    }
}
