package de.jawollo07.manhunt.player;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Bridges Bukkit connection events to the active game session.
 * All handlers run synchronously on the Paper server thread.
 */
public final class PlayerSessionListener implements Listener {
    private final GameManager gameManager;
    private final GameLifecycleService lifecycleService;
    private final PlayerSnapshotService snapshotService;

    public PlayerSessionListener(
            GameManager gameManager,
            GameLifecycleService lifecycleService,
            PlayerSnapshotService snapshotService
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getActiveGame().orElse(null);

        if (game != null && game.hasPlayer(player.getUniqueId())
                && game.getState() != GameState.ENDING
                && game.getState() != GameState.RESETTING) {
            try {
                lifecycleService.playerJoined(player.getUniqueId());
            } catch (RuntimeException exception) {
                org.bukkit.Bukkit.getLogger().severe("Could not restore ManHunt session for "
                        + player.getName() + ": " + exception.getMessage());
                snapshotService.restore(player);
            }
            return;
        }

        // A player who disconnected during a game may reconnect after the
        // game ended. Their original pre-game state must still be restored.
        try {
            snapshotService.restore(player);
        } catch (RuntimeException exception) {
            org.bukkit.Bukkit.getLogger().severe("Could not restore player snapshot for "
                    + player.getName() + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || !game.hasPlayer(player.getUniqueId())) return;
        if (game.getState() == GameState.ENDING || game.getState() == GameState.RESETTING) return;

        // Lobby departures are roster changes, not mode events. Calling a
        // mode callback here can access teams that do not exist until prepare().
        if (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN) {
            gameManager.removePlayer(player.getUniqueId());
            org.bukkit.Bukkit.broadcastMessage("§e[ManHunt] §7" + player.getName()
                    + " hat die Lobby verlassen. §f" + game.getPlayerCount() + "/"
                    + game.getSettings().maxPlayers() + " §7Spieler.");
            return;
        }

        try {
            lifecycleService.playerQuit(player.getUniqueId());
        } catch (RuntimeException exception) {
            org.bukkit.Bukkit.getLogger().severe("Could not process ManHunt disconnect for "
                    + player.getName() + ": " + exception.getMessage());
        }
    }
}
