package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.storage.StorageManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** SQLite persistence for arena definitions and their named spawn points. */
public final class ArenaRepository {
    private final StorageManager storage;

    public ArenaRepository(StorageManager storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public void save(Arena arena) throws SQLException {
        Objects.requireNonNull(arena, "arena");
        try (Connection connection = storage.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO arenas (id, display_name, world_name)
                        VALUES (?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            display_name = excluded.display_name,
                            world_name = excluded.world_name
                        """)) {
                    statement.setString(1, arena.id());
                    statement.setString(2, arena.displayName());
                    statement.setString(3, arena.worldName());
                    statement.executeUpdate();
                }
                try (PreparedStatement deleteModes = connection.prepareStatement(
                        "DELETE FROM arena_enabled_modes WHERE arena_id = ?")) {
                    deleteModes.setString(1, arena.id());
                    deleteModes.executeUpdate();
                }
                try (PreparedStatement insertMode = connection.prepareStatement(
                        "INSERT INTO arena_enabled_modes (arena_id, mode_id) VALUES (?, ?)")) {
                    for (String modeId : arena.enabledModeIds()) {
                        insertMode.setString(1, arena.id());
                        insertMode.setString(2, modeId);
                        insertMode.addBatch();
                    }
                    insertMode.executeBatch();
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM arena_spawn_points WHERE arena_id = ?")) {
                    delete.setString(1, arena.id());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO arena_spawn_points
                            (arena_id, name, x, y, z, yaw, pitch)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (Map.Entry<String, ArenaSpawnPoint> entry : arena.spawnPoints().entrySet()) {
                        ArenaSpawnPoint point = entry.getValue();
                        insert.setString(1, arena.id());
                        insert.setString(2, entry.getKey());
                        insert.setDouble(3, point.x());
                        insert.setDouble(4, point.y());
                        insert.setDouble(5, point.z());
                        insert.setFloat(6, point.yaw());
                        insert.setFloat(7, point.pitch());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public Optional<Arena> findById(String id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, display_name, world_name FROM arenas WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(readArena(connection,
                        result.getString("id"),
                        result.getString("display_name"),
                        result.getString("world_name")));
            }
        }
    }

    public List<Arena> findAll() throws SQLException {
        List<Arena> arenas = new ArrayList<>();
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, display_name, world_name FROM arenas ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                arenas.add(readArena(connection,
                        result.getString("id"),
                        result.getString("display_name"),
                        result.getString("world_name")));
            }
        }
        return List.copyOf(arenas);
    }

    public boolean delete(String id) throws SQLException {
        Objects.requireNonNull(id, "id");
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM arenas WHERE id = ?")) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Arena readArena(Connection connection, String id, String displayName, String worldName)
            throws SQLException {
        Map<String, ArenaSpawnPoint> points = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name, x, y, z, yaw, pitch
                FROM arena_spawn_points WHERE arena_id = ? ORDER BY name
                """)) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("name");
                    points.put(name, new ArenaSpawnPoint(
                            name,
                            result.getDouble("x"),
                            result.getDouble("y"),
                            result.getDouble("z"),
                            result.getFloat("yaw"),
                            result.getFloat("pitch")
                    ));
                }
            }
        }
        java.util.Set<String> enabledModes = new java.util.LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT mode_id FROM arena_enabled_modes WHERE arena_id = ? ORDER BY mode_id")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) enabledModes.add(result.getString("mode_id"));
            }
        }
        return new Arena(id, displayName, worldName, points, enabledModes);
    }
}
