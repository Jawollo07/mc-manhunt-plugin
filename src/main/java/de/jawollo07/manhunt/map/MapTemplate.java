package de.jawollo07.manhunt.map;

/** Read-only status information about a world folder that may be used as a map template. */
public record MapTemplate(String id, boolean valid, boolean loaded, String status) {
    public MapTemplate {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Template id must not be blank");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("Template status must not be blank");
    }

    public boolean canBeCopiedNow() {
        return valid && !loaded;
    }
}
