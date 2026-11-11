package de.jawollo07.manhunt.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class PlayerManager {
    public Optional<Player> findOnlinePlayer(UUID playerId) {
        return Optional.ofNullable(Bukkit.getPlayer(playerId));
    }

    public Player requireOnlinePlayer(UUID playerId) {
        return findOnlinePlayer(playerId).orElseThrow(
                () -> new IllegalArgumentException("Player is not online: " + playerId)
        );
    }

    public boolean isOnline(UUID playerId) {
        return Bukkit.getPlayer(playerId) != null;
    }
}
