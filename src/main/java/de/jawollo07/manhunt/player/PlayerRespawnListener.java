package de.jawollo07.manhunt.player;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.game.GameState;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Objects;

/**
 * Keeps in-round respawns inside the disposable arena world. This is
 * especially important for Infection, where a dead survivor becomes infected
 * and must return to the infected team's spawn instead of the server spawn.
 */
public final class PlayerRespawnListener implements Listener {
    private final GameManager gameManager;
    private final GameLifecycleService lifecycle;

    public PlayerRespawnListener(GameManager gameManager, GameLifecycleService lifecycle) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || game.getState() != GameState.RUNNING
                || !game.hasPlayer(event.getPlayer().getUniqueId())) return;

        Location arenaSpawn = lifecycle.getRespawnLocation(event.getPlayer().getUniqueId());
        if (arenaSpawn != null) event.setRespawnLocation(arenaSpawn);
    }
}
