package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.player.PlayerStatus;
import de.jawollo07.manhunt.teams.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Live roster for the active round, shared by participants and administrators. */
public final class PlayerOverviewMenu implements Listener {
    private static final int SIZE = 54;
    private final ManHuntPlugin plugin;
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    public PlayerOverviewMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) {
            viewer.sendMessage("§eAktuell ist keine Runde aktiv.");
            return;
        }
        RosterHolder holder = new RosterHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                org.bukkit.ChatColor.GOLD + "ManHunt • Spielerübersicht");
        holder.inventory = inventory;
        render(game, inventory);
        viewer.openInventory(inventory);
        BukkitTask old = refreshTasks.remove(viewer.getUniqueId());
        if (old != null) old.cancel();
        refreshTasks.put(viewer.getUniqueId(), Bukkit.getScheduler().runTaskTimer(
                plugin, () -> {
                    if (!viewer.isOnline() || !(viewer.getOpenInventory().getTopInventory().getHolder()
                            instanceof RosterHolder)) {
                        stopRefresh(viewer.getUniqueId());
                        return;
                    }
                    Game current = plugin.getGameManager().getActiveGame().orElse(null);
                    if (current == null) {
                        viewer.closeInventory();
                        viewer.sendMessage("§eDie Runde ist nicht mehr aktiv.");
                        return;
                    }
                    render(current, inventory);
                }, 20L, 20L));
    }

    private void render(Game game, Inventory inventory) {
        inventory.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot > 44) inventory.setItem(slot, filler);
        }

        long active = game.getPlayers().stream().filter(GamePlayer::isActiveForRound).count();
        long eliminated = game.getPlayers().stream().filter(GamePlayer::isEliminated).count();
        long disconnected = game.getPlayers().stream().filter(p -> !p.isConnected()).count();
        inventory.setItem(4, item(Material.PLAYER_HEAD, "§6Spielerübersicht", List.of(
                "§7Modus: §f" + game.getMode().getDisplayName(),
                "§7Status: §f" + stateLabel(game.getState()),
                "§7Spieler: §f" + game.getPlayerCount() + "/" + game.getSettings().maxPlayers(),
                "§aAktiv: " + active + "  §cAusgeschieden: " + eliminated,
                "§eGetrennt: " + disconnected,
                "§8Aktualisiert sich automatisch"
        )));

        List<GamePlayer> participants = new ArrayList<>(game.getPlayers());
        participants.sort(Comparator
                .comparing((GamePlayer p) -> game.getTeamManager().getTeamOf(p.getUniqueId())
                        .map(Team::getName).orElse("~"))
                .thenComparing(p -> playerName(p.getUniqueId()), String.CASE_INSENSITIVE_ORDER));

        int slot = 9;
        for (GamePlayer participant : participants) {
            if (slot > 44) break;
            UUID id = participant.getUniqueId();
            Player online = Bukkit.getPlayer(id);
            Team team = game.getTeamManager().getTeamOf(id).orElse(null);
            String role = game.getRoleManager().getRoleId(id).map(this::roleLabel).orElse("Keine Rolle");
            String teamName = team == null ? "Kein Team" : team.getName();
            ChatColor teamColor = team == null ? ChatColor.GRAY : teamColor(team);
            List<String> lore = new ArrayList<>();
            lore.add("§7Team: " + teamColor + teamName);
            lore.add("§7Rolle: §f" + role);
            lore.add("§7Status: " + statusColor(participant) + statusLabel(participant));
            if (online != null && online.isOnline()) {
                lore.add("§7Verbindung: §aOnline");
                lore.add("§7Ping: §f" + Math.max(0, online.getPing()) + " ms");
            } else {
                lore.add("§7Verbindung: §cOffline");
                lore.add("§7" + disconnectLabel(game, participant));
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                meta.setOwningPlayer(offline);
                meta.setDisplayName((online == null ? "§7" : "§f") + playerName(id));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
        }
        inventory.setItem(49, item(Material.CLOCK, "§eAktualisieren", List.of("§7Die Übersicht wird automatisch aktualisiert.")));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen", List.of("§7Menü schließen")));
    }

    private String disconnectLabel(Game game, GamePlayer participant) {
        if (participant.isConnected()) return "Nicht verbunden";
        int grace = game.getArena().modeIntSetting("general", "disconnect_grace_seconds", 60);
        if (grace <= 0) return "Kulanz: unbegrenzt";
        Instant disconnectedAt = participant.getDisconnectedAt().orElse(null);
        if (disconnectedAt == null) return "Kulanzzeit wird geprüft";
        long remaining = Math.max(0, grace - Duration.between(disconnectedAt, Instant.now()).getSeconds());
        return remaining > 0 ? "Kulanz: noch " + remaining + "s" : "Kulanz abgelaufen";
    }

    private String playerName(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString().substring(0, 8) : name;
    }

    private String statusLabel(GamePlayer participant) {
        if (!participant.isConnected()) return "Getrennt";
        return switch (participant.getStatus()) {
            case WAITING -> "Wartet";
            case PLAYING -> "Lebt";
            case ELIMINATED -> "Ausgeschieden";
            case SPECTATING -> "Zuschauer";
            case DISCONNECTED -> "Getrennt";
        };
    }

    private ChatColor statusColor(GamePlayer participant) {
        if (!participant.isConnected()) return ChatColor.YELLOW;
        return switch (participant.getStatus()) {
            case PLAYING -> ChatColor.GREEN;
            case ELIMINATED -> ChatColor.RED;
            case SPECTATING -> ChatColor.GRAY;
            case WAITING -> ChatColor.YELLOW;
            case DISCONNECTED -> ChatColor.YELLOW;
        };
    }

    private String roleLabel(String role) {
        return switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "assassin" -> "Assassine";
            case "hitman" -> "Hitman";
            case "survivor" -> "Überlebender";
            case "juggernaut" -> "Juggernaut";
            case "hunter" -> "Jäger";
            case "infected" -> "Infiziert";
            default -> role;
        };
    }

    private String stateLabel(GameState state) {
        return switch (state) {
            case WAITING -> "Wartet";
            case LOBBY -> "Lobby";
            case COUNTDOWN -> "Countdown";
            case STARTING -> "Startet";
            case RUNNING -> "Läuft";
            case ENDING -> "Wird beendet";
            case RESETTING -> "Wird zurückgesetzt";
        };
    }

    private ChatColor teamColor(Team team) {
        return switch (team.getColor().toUpperCase(java.util.Locale.ROOT)) {
            case "DARK_RED", "RED" -> ChatColor.RED;
            case "GREEN", "DARK_GREEN" -> ChatColor.GREEN;
            case "BLUE", "DARK_BLUE" -> ChatColor.BLUE;
            case "AQUA" -> ChatColor.AQUA;
            case "YELLOW" -> ChatColor.YELLOW;
            case "GOLD" -> ChatColor.GOLD;
            case "DARK_PURPLE", "LIGHT_PURPLE" -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.WHITE;
        };
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void stopRefresh(UUID id) {
        BukkitTask task = refreshTasks.remove(id);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RosterHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49) {
            Game game = plugin.getGameManager().getActiveGame().orElse(null);
            if (game != null) render(game, event.getView().getTopInventory());
        } else if (event.getRawSlot() == 53) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RosterHolder) {
            stopRefresh(event.getPlayer().getUniqueId());
        }
    }

    private static final class RosterHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
