package de.jawollo07.manhunt.player;

import de.jawollo07.manhunt.storage.StorageManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable storage for pre-game player snapshots.
 */
public final class PlayerSnapshotRepository {
    private final StorageManager storage;

    public PlayerSnapshotRepository(StorageManager storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public void save(UUID playerId, PlayerSnapshot snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_snapshots (player_uuid, payload, created_at)
                     VALUES (?, ?, ?)
                     ON CONFLICT(player_uuid) DO UPDATE SET
                         payload = excluded.payload,
                         created_at = excluded.created_at
                     """)) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, snapshot.serialize());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Could not persist player snapshot for " + playerId, exception);
        }
    }

    public Map<UUID, PlayerSnapshot> loadAll() {
        Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, payload FROM player_snapshots");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                snapshots.put(playerId, PlayerSnapshot.deserialize(result.getBytes("payload")));
            }
            return snapshots;
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load persisted player snapshots", exception);
        }
    }

    public void delete(UUID playerId) {
        try (Connection connection = storage.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM player_snapshots WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete player snapshot for " + playerId, exception);
        }
    }
}
