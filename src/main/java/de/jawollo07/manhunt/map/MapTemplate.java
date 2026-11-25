package de.jawollo07.manhunt.map;

/** Read-only status and preview information about a world folder usable as a map template. */
public record MapTemplate(String id, boolean valid, boolean loaded, String status,
                          String folder, String lastModified) {
    public MapTemplate {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Template id must not be blank");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("Template status must not be blank");
        folder = folder == null || folder.isBlank() ? "Unbekannt" : folder;
        lastModified = lastModified == null || lastModified.isBlank() ? "Unbekannt" : lastModified;
    }

    /** Backwards-compatible constructor for callers that only provide status information. */
    public MapTemplate(String id, boolean valid, boolean loaded, String status) {
        this(id, valid, loaded, status, "Unbekannt", "Unbekannt");
    }

    public boolean canBeCopiedNow() {
        return valid && !loaded;
    }
}
