package de.jawollo07.manhunt.map;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MapInstance(UUID id, String templateId, String worldName, Instant createdAt) {
    public MapInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("Template id must not be blank");
        if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("World name must not be blank");
    }

    public static MapInstance create(String templateId, String worldName) {
        return new MapInstance(UUID.randomUUID(), templateId, worldName, Instant.now());
    }
}
