package de.jawollo07.manhunt.player;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.game.GameLifecycleService;
import de.jawollo07.manhunt.game.GameManager;
import de.jawollo07.manhunt.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;

/** Routes in-round deaths to the active mode without allowing vanilla item loss. */
public final class PlayerDeathListener implements Listener {
    private final GameManager gameManager;
    private final GameLifecycleService lifecycle;

    public PlayerDeathListener(GameManager gameManager, GameLifecycleService lifecycle) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || game.getState() != GameState.RUNNING || !game.hasPlayer(victim.getUniqueId())) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        Player killer = victim.getKiller();
        lifecycle.playerDied(victim.getUniqueId(), killer == null ? null : killer.getUniqueId());
    }
}
