package de.jawollo07.manhunt.arena;

import java.util.Objects;

public record Arena(String id, String displayName, String worldName) {
    public Arena {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Arena id must not be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Arena display name must not be blank");
        if (worldName == null || worldName.isBlank()) throw new IllegalArgumentException("World name must not be blank");
    }
}
