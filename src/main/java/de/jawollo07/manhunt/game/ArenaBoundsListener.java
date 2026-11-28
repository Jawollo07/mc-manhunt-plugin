package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.arena.ArenaBounds;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Enforces the exact rectangular arena cuboid; Bukkit's visible world border is square. */
public final class ArenaBoundsListener implements Listener {
    private final GameManager gameManager;

    public ArenaBoundsListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || !game.isRunning()
                || game.getPlayer(event.getPlayer().getUniqueId()).isEmpty()) return;
        var mapInstance = game.getMapInstance().orElse(null);
        if (mapInstance == null || !mapInstance.worldName().equals(event.getFrom().getWorld().getName())) return;
        if (event.getTo().getWorld() == null
                || !mapInstance.worldName().equals(event.getTo().getWorld().getName())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("§cDu kannst die Arena nicht verlassen."));
            return;
        }

        ArenaBounds bounds;
        try {
            bounds = ArenaBounds.from(game.getArena()).orElse(null);
        } catch (IllegalArgumentException exception) {
            event.setCancelled(true);
            return;
        }
        if (bounds == null || bounds.contains(event.getTo())) return;

        event.setTo(event.getFrom());
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("§cDu kannst die Arena nicht verlassen."));
    }
}
