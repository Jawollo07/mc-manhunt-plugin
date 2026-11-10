package de.jawollo07.manhunt.player;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class PlayerManager {
    public void addPlayer(UUID playerId) {

    }
    public void removePlayer(UUID playerId) {

    }
    public Player getPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player with ID " + playerId + " not found.");
        }
        return player;
    }
}
