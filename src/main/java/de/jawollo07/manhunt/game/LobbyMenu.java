package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.modes.GameModeFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class LobbyMenu implements Listener {
    private static final int SIZE = 54;
    private static final int ACTION_SLOT = 48;
    private static final int ADMIN_SLOT = 45;
    private static final int ARENA_SLOT = 50;
    private static final int REFRESH_SLOT = 53;
    private final ManHuntPlugin plugin;

    public LobbyMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenMenus, 40L, 40L);
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                render(player, player.getOpenInventory().getTopInventory());
            }
        }
    }

    public void open(Player player) {
        InventoryHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ManHunt • Lobby"));
        ((MenuHolder) holder).inventory = inventory;
        render(player, inventory);
        player.openInventory(inventory);
    }

    private void render(Player viewer, Inventory inventory) {
        inventory.clear();
        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) {
            inventory.setItem(4, item(Material.BOOK, "§eKeine aktive Runde",
                    "§7Aktuell wartet keine Runde auf Spieler.",
                    "§7Ein Admin kann eine Runde erstellen."));
            int slot = 10;
            for (GameModeFactory factory : plugin.getGameModeRegistry().getAll()) {
                if (slot > 16) break;
                inventory.setItem(slot++, item(Material.NAME_TAG, "§e" + factory.getDisplayName(),
                        "§7Modus: §f" + factory.getId(),
                        "§8Die Modusauswahl erfolgt beim Erstellen",
                        "§8einer Runde durch einen Admin."));
            }
            inventory.setItem(ACTION_SLOT, item(viewer.hasPermission("manhunt.admin") ? Material.EMERALD : Material.BARRIER,
                    viewer.hasPermission("manhunt.admin") ? "§a§lRunde erstellen" : "§cNoch keine Runde",
                    viewer.hasPermission("manhunt.admin") ? "§7Öffnet die Admin-Auswahl für Modus und Arena."
                            : "§7Sobald ein Admin eine Runde erstellt,",
                    viewer.hasPermission("manhunt.admin") ? "§eKlicken zum Erstellen" : "§7kannst du hier direkt beitreten."));
            inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "§eAktualisieren", "§7Lobby neu laden"));
            return;
        }

        long onlineCount = game.getPlayers().stream()
                .filter(participant -> Bukkit.getPlayer(participant.getUniqueId()) != null).count();
        int required = game.getSettings().minPlayers();
        boolean enoughPlayers = onlineCount >= required;
        inventory.setItem(2, item(Material.CLOCK, "§6" + stateName(game.getState()),
                "§7Status: §f" + game.getState(),
                game.getState() == GameState.COUNTDOWN
                        ? "§eStart in: §f" + Math.max(0, game.getCountdownRemainingSeconds()) + " Sekunden"
                        : "§7" + (enoughPlayers ? "Genug Spieler – Start möglich." : "Es fehlen noch "
                                + Math.max(0, required - (int) onlineCount) + " Spieler.")));
        inventory.setItem(4, item(Material.COMPASS, "§e" + game.getMode().getDisplayName(),
                "§7Spielmodus: §f" + game.getMode().getId(),
                "§7Arena: §f" + game.getArena().displayName(),
                "§7Teams: §f" + teamSummary(game)));
        inventory.setItem(6, item(Material.PLAYER_HEAD, "§aSpieler: §f" + onlineCount
                        + "§7/§f" + game.getSettings().maxPlayers(),
                "§7Mindestens benötigt: §f" + required,
                "§7Freie Plätze: §f" + game.getAvailableSlots(),
                "§7Offline-Spieler werden in der Lobby nicht mitgezählt."));

        Material readiness = enoughPlayers ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
        inventory.setItem(13, item(readiness,
                enoughPlayers ? "§aBereit für den Spielstart" : "§eWarte auf weitere Spieler",
                "§7Online: §f" + onlineCount + "§7/§f" + required + " §7benötigt",
                game.getState() == GameState.COUNTDOWN
                        ? "§eCountdown: §f" + Math.max(0, game.getCountdownRemainingSeconds()) + "s"
                        : enoughPlayers ? "§7Der Countdown startet automatisch."
                        : "§7Sobald genug Spieler online sind,",
                game.getState() == GameState.COUNTDOWN && onlineCount < required
                        ? "§cDer Countdown wird bei zu wenigen Spielern pausiert."
                        : "§7kann der Countdown beginnen."));

        int slot = 9;
        for (GamePlayer participant : game.getPlayers()) {
            if (slot > 44) break;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(participant.getUniqueId());
            String name = offline.getName();
            if (name == null) name = participant.getUniqueId().toString().substring(0, 8);
            boolean online = offline.isOnline();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offline);
                meta.setDisplayName((online ? "§a" : "§7") + name);
                List<String> lore = new ArrayList<>();
                lore.add(online ? "§aOnline – bereit für die Runde" : "§cOffline – nicht startberechtigt");
                String teamPreference = game.getLobbyTeamPreference(participant.getUniqueId()).orElse("");
                lore.add("§7Teamwunsch: §f" + (teamPreference.isEmpty() ? "Automatisch" : lobbyTeamLabel(teamPreference)));
                lore.add("§7Status: §f" + participant.getStatus());
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
        }
        while (slot <= 44) inventory.setItem(slot++, item(Material.GRAY_STAINED_GLASS_PANE, "§8Freier Platz"));

        boolean canJoin = (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN)
                && !game.hasPlayer(viewer.getUniqueId()) && game.getAvailableSlots() > 0;
        boolean canLeave = (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN) && game.hasPlayer(viewer.getUniqueId());
        if (canJoin) {
            inventory.setItem(ACTION_SLOT, item(Material.LIME_DYE, "§a§lRunde beitreten",
                    "§7Klicke hier, um mitzuspielen."));
        } else if (canLeave) {
            inventory.setItem(ACTION_SLOT, item(Material.RED_DYE, "§c§lRunde verlassen",
                    "§7Klicke hier, um die Lobby zu verlassen."));
        } else {
            inventory.setItem(ACTION_SLOT, item(Material.BARRIER, "§cBeitritt nicht möglich",
                    "§7Die Runde läuft bereits, ist voll",
                    "§7oder du bist bereits dabei."));
        }
        inventory.setItem(ARENA_SLOT, item(Material.MAP, "§eArena: " + game.getArena().displayName(),
                "§7Die Arena wird vom Admin festgelegt."));
        if (viewer.hasPermission("manhunt.admin")) {
            inventory.setItem(ADMIN_SLOT, item(Material.COMMAND_BLOCK, "§cAdmin-Menü",
                    "§7Runde verwalten oder eine neue Runde vorbereiten."));
        }
        inventory.setItem(47, item(Material.SHIELD, "§bTeamübersicht",
                teamLore(game).toArray(String[]::new)));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "§eAktualisieren",
                "§7Spielerliste und Status neu laden."));
    }

    private String teamSummary(Game game) {
        return teamLore(game).stream().map(line -> line.replace("§7", "").replace("§f", ""))
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    private List<String> teamLore(Game game) {
        List<String> teams = switch (game.getMode().getId().toLowerCase(java.util.Locale.ROOT)) {
            case "hitman" -> List.of("hitmen", "survivors");
            case "juggernaut" -> List.of("juggernaut", "hunters");
            case "infection" -> List.of("infected", "survivors");
            default -> List.of();
        };
        if (teams.isEmpty()) return List.of("§7Dieser Modus hat keine festen Teams.");
        List<String> lines = new ArrayList<>();
        for (String key : teams) {
            long count = game.getPlayers().stream()
                    .filter(participant -> game.getLobbyTeamPreference(participant.getUniqueId())
                            .orElse("").equals(key)).count();
            lines.add("§7" + lobbyTeamLabel(key) + ": §f" + count + " zugewiesen");
        }
        long automatic = game.getPlayers().stream()
                .filter(participant -> game.getLobbyTeamPreference(participant.getUniqueId()).isEmpty()).count();
        lines.add("§7Automatische Verteilung: §f" + automatic);
        lines.add("§8Wunschzuweisungen können im Admin-Menü geändert werden.");
        return lines;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        int slot = event.getRawSlot();
        if (slot == ADMIN_SLOT && player.hasPermission("manhunt.admin")) {
            plugin.getAdminMenu().open(player);
            return;
        }
        if (slot == REFRESH_SLOT) {
            render(player, event.getView().getTopInventory());
            return;
        }
        if (slot != ACTION_SLOT) return;

        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) {
            if (player.hasPermission("manhunt.admin")) plugin.getAdminMenu().open(player);
            else render(player, event.getView().getTopInventory());
            return;
        }
        boolean canJoin = (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN)
                && !game.hasPlayer(player.getUniqueId()) && game.getAvailableSlots() > 0;
        boolean canLeave = (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN) && game.hasPlayer(player.getUniqueId());
        if (!canJoin && !canLeave) {
            render(player, event.getView().getTopInventory());
            return;
        }

        player.closeInventory();
        Bukkit.dispatchCommand(player, canJoin ? "manhunt game join" : "manhunt game leave");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String lobbyTeamLabel(String key) {
        return switch (key) {
            case "hitmen" -> "Hitmen";
            case "survivors" -> "Überlebende";
            case "juggernaut" -> "Juggernaut";
            case "hunters" -> "Jäger";
            case "infected" -> "Infizierte";
            default -> key;
        };
    }

    private String stateName(GameState state) {
        return switch (state) {
            case WAITING, LOBBY -> "Lobby – Spieler gesucht";
            case COUNTDOWN -> "Countdown";
            case STARTING -> "Spiel startet";
            case RUNNING -> "Runde läuft";
            case ENDING -> "Runde wird beendet";
            case RESETTING -> "Runde wird zurückgesetzt";
        };
    }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
