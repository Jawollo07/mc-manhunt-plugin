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
            lifecycleService.playerJoined(player.getUniqueId());
            return;
        }

        // A player who disconnected during a game may reconnect after the
        // game ended. Their original pre-game state must still be restored.
        snapshotService.restore(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || !game.hasPlayer(player.getUniqueId())) return;
        if (game.getState() == GameState.ENDING || game.getState() == GameState.RESETTING) return;

        lifecycleService.playerQuit(player.getUniqueId());
    }
}
