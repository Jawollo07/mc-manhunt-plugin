package de.jawollo07.manhunt.arena;

import org.bukkit.Location;
import org.bukkit.World;

public record ArenaSpawnPoint(
        String name,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public ArenaSpawnPoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spawn point name must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Spawn point coordinates and rotation must be finite");
        }
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
