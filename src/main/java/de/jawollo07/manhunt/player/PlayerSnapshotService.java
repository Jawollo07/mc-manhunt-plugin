package de.jawollo07.manhunt.player;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures and restores player state around a game.
 * All methods must be called on the Paper server thread.
 */
public final class PlayerSnapshotService {
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final PlayerSnapshotRepository repository;

    public PlayerSnapshotService(PlayerSnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        snapshots.putAll(repository.loadAll());
    }

    public boolean captureIfAbsent(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        if (snapshots.containsKey(playerId)) return false;
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        repository.save(playerId, snapshot);
        snapshots.put(playerId, snapshot);
        return true;
    }

    public void capture(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        repository.save(playerId, snapshot);
        snapshots.put(playerId, snapshot);
    }

    public boolean hasSnapshot(UUID playerId) {
        return snapshots.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Removes the durable record only after the in-game state was restored.
     */
    public boolean restore(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        PlayerSnapshot snapshot = snapshots.get(playerId);
        if (snapshot == null) return false;
        snapshot.restore(player);
        repository.delete(playerId);
        snapshots.remove(playerId);
        return true;
    }

    public boolean discard(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!snapshots.containsKey(playerId)) return false;
        repository.delete(playerId);
        snapshots.remove(playerId);
        return true;
    }

    public int size() {
        return snapshots.size();
    }
}
