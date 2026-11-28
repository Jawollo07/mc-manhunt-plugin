package de.jawollo07.manhunt.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/** A persisted, axis-aligned 3D cuboid used to constrain one arena. */
public record ArenaBounds(double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ) {
    public ArenaBounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Ungültige Arena-Begrenzung.");
        }
    }

    public static Optional<ArenaBounds> from(Arena arena) {
        String[] keys = {"bounds_pos1_x", "bounds_pos1_y", "bounds_pos1_z",
                "bounds_pos2_x", "bounds_pos2_y", "bounds_pos2_z"};
        String[] values = new String[keys.length];
        boolean any = false;
        for (int i = 0; i < keys.length; i++) {
            values[i] = arena.modeSetting("general", keys[i], "");
            any |= !values[i].isBlank();
        }
        if (!any) return Optional.empty();
        for (String value : values) {
            if (value == null || value.isBlank()) return Optional.empty();
        }
        try {
            double x1 = Double.parseDouble(values[0]), y1 = Double.parseDouble(values[1]);
            double z1 = Double.parseDouble(values[2]), x2 = Double.parseDouble(values[3]);
            double y2 = Double.parseDouble(values[4]), z2 = Double.parseDouble(values[5]);
            return Optional.of(new ArenaBounds(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Die gespeicherten Arena-Eckpunkte sind ungültig.", exception);
        }
    }

    public static boolean hasAny(Arena arena) {
        return ListHolder.KEYS.stream().anyMatch(key -> !arena.modeSetting("general", key, "").isBlank());
    }

    private static final class ListHolder {
        private static final java.util.List<String> KEYS = java.util.List.of("bounds_pos1_x", "bounds_pos1_y",
                "bounds_pos1_z", "bounds_pos2_x", "bounds_pos2_y", "bounds_pos2_z");
    }

    public static ArenaBounds fromCorners(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            throw new IllegalArgumentException("Beide Eckpunkte müssen in derselben Welt liegen.");
        }
        return new ArenaBounds(
                Math.min(first.getBlockX(), second.getBlockX()), Math.min(first.getBlockY(), second.getBlockY()),
                Math.min(first.getBlockZ(), second.getBlockZ()), Math.max(first.getBlockX(), second.getBlockX()),
                Math.max(first.getBlockY(), second.getBlockY()), Math.max(first.getBlockZ(), second.getBlockZ()));
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX + 1.0
                && y >= minY && y <= maxY + 1.0
                && z >= minZ && z <= maxZ + 1.0;
    }

    public boolean contains(Location location) {
        return location != null && contains(location.getX(), location.getY(), location.getZ());
    }

    public void applyWorldBorder(World world) {
        var border = world.getWorldBorder();
        border.setCenter((minX + maxX + 1.0) / 2.0, (minZ + maxZ + 1.0) / 2.0);
        border.setSize(Math.max(1.0, Math.max(maxX - minX + 1.0, maxZ - minZ + 1.0)));
        border.setWarningDistance(0);
        border.setDamageAmount(0.0);
    }

    public Arena applyTo(Arena arena) {
        return arena.withModeSetting("general", "bounds_pos1_x", Double.toString(minX))
                .withModeSetting("general", "bounds_pos1_y", Double.toString(minY))
                .withModeSetting("general", "bounds_pos1_z", Double.toString(minZ))
                .withModeSetting("general", "bounds_pos2_x", Double.toString(maxX))
                .withModeSetting("general", "bounds_pos2_y", Double.toString(maxY))
                .withModeSetting("general", "bounds_pos2_z", Double.toString(maxZ));
    }
}
