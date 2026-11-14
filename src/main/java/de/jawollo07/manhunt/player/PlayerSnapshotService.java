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

    /**
     * Captures a player only once. Rejoining or a repeated lifecycle callback
     * must never replace the original pre-game state with an in-game state.
     *
     * @return true when a new snapshot was captured
     */
    public boolean captureIfAbsent(Player player) {
        Objects.requireNonNull(player, "player");
        if (snapshots.containsKey(player.getUniqueId())) {
            return false;
        }
        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        return true;
    }

    /**
     * Explicit capture for callers that intentionally want to replace a snapshot.
     */
    public void capture(Player player) {
        Objects.requireNonNull(player, "player");
        snapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
    }

    public boolean hasSnapshot(UUID playerId) {
        return snapshots.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Restores the snapshot and removes it only after restoration returns.
     * Returns false when no snapshot exists for this player.
     */
    public boolean restore(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerSnapshot snapshot = snapshots.get(player.getUniqueId());
        if (snapshot == null) return false;
        snapshot.restore(player);
        snapshots.remove(player.getUniqueId());
        return true;
    }

    public boolean discard(UUID playerId) {
        return snapshots.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    public int size() {
        return snapshots.size();
    }
}
