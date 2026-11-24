package de.jawollo07.manhunt.arena;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Validates arena definitions before they are persisted. */
public final class ArenaService {
    private final ArenaRepository repository;

    public ArenaService(ArenaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void save(Arena arena) throws SQLException {
        Objects.requireNonNull(arena, "arena");
        validateIdentifier(arena.id(), "arena id");
        validateTemplateIdentifier(arena.worldName());
        repository.save(arena);
    }

    public Optional<Arena> find(String id) throws SQLException {
        return repository.findById(id);
    }

    public List<Arena> findAll() throws SQLException {
        return repository.findAll();
    }

    public boolean delete(String id) throws SQLException {
        validateIdentifier(id, "arena id");
        return repository.delete(id);
    }

    private void validateTemplateIdentifier(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,48}")) {
            throw new IllegalArgumentException("Invalid map template id: " + value);
        }
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }
}
