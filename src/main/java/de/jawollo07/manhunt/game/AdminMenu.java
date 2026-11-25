package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.arena.ArenaValidation;
import de.jawollo07.manhunt.modes.GameModeFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminMenu implements Listener {
    private static final int SIZE = 54;
    private static final int FIRST_SLOT = 10;
    private static final int LAST_SLOT = 43;
    private final ManHuntPlugin plugin;

    public AdminMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!player.hasPermission("manhunt.admin")) {
            player.sendMessage("§cDafür hast du keine Berechtigung.");
            return;
        }
        if (plugin.getGameManager().getActiveGame().isPresent()) openOverview(player);
        else openModes(player);
    }

    private void openOverview(Player player) {
        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) {
            openModes(player);
            return;
        }
        MenuHolder holder = new MenuHolder(Screen.OVERVIEW, null, null, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("ManHunt • Rundenverwaltung"));
        holder.inventory = inventory;
        inventory.setItem(4, item(Material.COMPASS, "§e" + game.getMode().getDisplayName(),
                "§7Arena: §f" + game.getArena().displayName(),
                "§7Status: §f" + game.getState(),
                "§7Spieler: §f" + game.getPlayerCount() + "/" + game.getSettings().maxPlayers()));
        int slot = 9;
        for (GamePlayer participant : game.getPlayers()) {
            if (slot > 17) break;
            String name = Bukkit.getOfflinePlayer(participant.getUniqueId()).getName();
            inventory.setItem(slot++, item(Material.PLAYER_HEAD, "§a" + (name == null ? participant.getUniqueId().toString().substring(0, 8) : name),
                    "§7Status: §f" + participant.getStatus()));
        }
        inventory.setItem(24, item(Material.MAP, "§aArenen verwalten",
                "§7Arenen, Vorlagen und Spawnpunkte bearbeiten."));
        if (game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY
                || game.getState() == GameState.COUNTDOWN) {
            inventory.setItem(26, item(Material.SHIELD, "§bTeams verwalten",
                    "§7Lobby-Teams ansehen und Spieler zuweisen.", "§eKlicken zum Öffnen"));
        }
        if (game.getState() == GameState.ENDING || game.getState() == GameState.RESETTING) {
            inventory.setItem(20, item(Material.CLOCK, "§eReset / Aufräumen",
                    "§7Stellt Spieler wieder her und entfernt die temporäre Map."));
        } else {
            inventory.setItem(20, item(Material.REDSTONE_BLOCK, "§cRunde stoppen",
                    "§7Beendet die aktuelle Runde und stellt Spieler wieder her."));
        }
        if (game.getState() == GameState.COUNTDOWN) {
            inventory.setItem(22, item(Material.RED_DYE, "§cCountdown abbrechen",
                    "§7Bringt die Lobby zurück in den Wartezustand."));
        } else if (game.getState() == GameState.LOBBY || game.getState() == GameState.WAITING) {
            inventory.setItem(22, item(Material.LIME_DYE, "§aCountdown starten",
                    "§7Startet den Countdown, sofern genug Spieler dabei sind."));
        } else {
            inventory.setItem(22, item(Material.BARRIER, "§cSchließen"));
        }
        player.openInventory(inventory);
    }

    private void openModes(Player player) {
        MenuHolder holder = new MenuHolder(Screen.MODES, null, null, 0);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ManHunt • Modus wählen"));
        holder.inventory = inventory;
        List<GameModeFactory> modes = new ArrayList<>(plugin.getGameModeRegistry().getAll());
        int slot = FIRST_SLOT;
        for (GameModeFactory mode : modes) {
            if (slot > LAST_SLOT) break;
            holder.entries.put(slot, mode.getId());
            inventory.setItem(slot++, item(Material.NAME_TAG, "§e" + mode.getDisplayName(),
                    "§7ID: §f" + mode.getId(), "§aKlicken, um diesen Modus auszuwählen"));
        }
        if (modes.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "§cKeine Spielmodi registriert"));
        inventory.setItem(45, item(Material.MAP, "§aArenen verwalten", "§7Kartenvorlagen und Spawnpunkte"));
        inventory.setItem(49, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openArenas(Player player, String modeId) {
        MenuHolder holder = new MenuHolder(Screen.ARENAS, modeId, null, 0);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ManHunt • Arena wählen"));
        holder.inventory = inventory;
        try {
            List<Arena> arenas = plugin.getArenaService().findAll();
            var mode = plugin.getGameModeRegistry().get(modeId)
                    .orElseThrow(() -> new IllegalStateException("Spielmodus ist nicht mehr verfügbar."))
                    .createSession();
            int slot = FIRST_SLOT;
            for (Arena arena : arenas) {
                if (slot > LAST_SLOT) break;
                holder.entries.put(slot, arena.id());
                var template = plugin.getMapManager().inspectTemplate(arena.worldName());
                ArenaValidation validation = ArenaValidation.check(arena, mode, template.valid(), template.loaded());
                Material icon = !validation.ready() ? Material.BARRIER
                        : validation.warnings().isEmpty() ? Material.MAP : Material.YELLOW_CONCRETE;
                String checkLine = !validation.errors().isEmpty()
                        ? "§cNicht startbereit: " + validation.errors().get(0)
                        : validation.warnings().isEmpty() ? "§aPrüfung: bereit"
                        : "§eOptionale Punkte: " + validation.warnings().stream()
                                .map(message -> message.replace("Optionaler Spawnpunkt fehlt: ", "")
                                        .replace("Kein Standard-Spawnpunkt gesetzt – der natürliche Weltspawn wird verwendet.", "Weltspawn-Fallback"))
                                .collect(java.util.stream.Collectors.joining(", "));
                inventory.setItem(slot++, item(icon, (validation.ready() ? "§a" : "§c") + arena.displayName(),
                        "§7Arena-ID: §f" + arena.id(),
                        "§7Vorlage: §f" + arena.worldName(),
                        "§7Vorlagenstatus: " + template.status(),
                        "§7Spawnpunkte: §f" + arena.spawnPoints().size(),
                        checkLine,
                        validation.ready() ? "§aKlicken für Einstellungen" : "§cVor Start erst Fehler beheben"));
            }
            if (arenas.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "§cKeine Arenen vorhanden",
                    "§7Lege zuerst eine Arena mit /manhunt arena create an."));
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().severe("Could not load arenas for admin menu: " + exception.getMessage());
            inventory.setItem(22, item(Material.BARRIER, "§cArenen konnten nicht geladen werden"));
        }
        inventory.setItem(45, item(Material.ARROW, "§eZurück zur Modusauswahl"));
        inventory.setItem(49, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openSettings(Player player, String modeId, String arenaId, int maxPlayers) {
        openSettings(player, modeId, arenaId, maxPlayers, true);
    }

    private void openSettings(Player player, String modeId, String arenaId, int maxPlayers, boolean randomTeams) {
        int minimum = minimumFor(modeId);
        MenuHolder holder = new MenuHolder(Screen.SETTINGS, modeId, arenaId, Math.max(minimum, maxPlayers), randomTeams);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("ManHunt • Spielerlimit"));
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.REDSTONE, "§cWeniger Spieler", "§7Maximalspieler verringern"));
        inventory.setItem(13, item(Material.PLAYER_HEAD, "§eMaximalspieler: §f" + holder.maxPlayers,
                "§7Mindestens: §f" + minimum, "§7Höchstens: §f32"));
        inventory.setItem(15, item(Material.EMERALD, "§aMehr Spieler", "§7Maximalspieler erhöhen"));
        inventory.setItem(17, item(holder.randomTeams ? Material.LIME_DYE : Material.GRAY_DYE,
                "§eZufällige Teams: " + (holder.randomTeams ? "§aAN" : "§cAUS"),
                "§7AN: Spieler werden zufällig verteilt.",
                "§7AUS: Reihenfolge des Lobby-Beitritts.", "§eKlicken zum Umschalten"));
        inventory.setItem(18, item(Material.ARROW, "§eZurück zur Arena-Auswahl"));
        inventory.setItem(22, item(Material.LIME_DYE, "§a§lLobby erstellen",
                "§7Modus: §f" + modeId, "§7Arena: §f" + arenaId,
                "§7Spielerlimit: §f" + holder.maxPlayers,
                "§7Zufällige Teams: " + (holder.randomTeams ? "§aJa" : "§cNein")));
        inventory.setItem(26, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void createGame(Player player, String modeId, String arenaId, int maxPlayers, boolean randomTeams) {
        if (!player.hasPermission("manhunt.admin")) {
            player.sendMessage("§cDafür hast du keine Berechtigung.");
            return;
        }
        if (plugin.getGameManager().hasActiveGame()) {
            player.sendMessage("§cEs existiert bereits eine aktive Runde. Beende oder setze sie zuerst zurück.");
            return;
        }
        try {
            GameModeFactory factory = plugin.getGameModeRegistry().get(modeId).orElseThrow(
                    () -> new IllegalStateException("Der Spielmodus ist nicht mehr verfügbar."));
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Die Arena ist nicht mehr verfügbar."));
            var template = plugin.getMapManager().inspectTemplate(arena.worldName());
            var mode = factory.createSession();
            ArenaValidation validation = ArenaValidation.check(arena, mode, template.valid(), template.loaded());
            if (!validation.ready()) {
                throw new IllegalStateException(String.join(" ", validation.errors()));
            }
            for (String warning : validation.warnings()) {
                player.sendMessage("§e[ManHunt] Hinweis: " + warning);
            }
            int minimum = Math.max(plugin.getConfigManager().getInt("game.min-players", 2), mode.getMinimumPlayers());
            GameSettings settings = new GameSettings(
                    minimum,
                    Math.max(minimum, maxPlayers),
                    plugin.getConfigManager().getInt("game.countdown-seconds", 10),
                    plugin.getConfigManager().getInt("game.time-limit-seconds", 2400),
                    plugin.getConfigManager().getBoolean("game.allow-late-join", false),
                    plugin.getConfigManager().getBoolean("game.allow-spectators", true),
                    randomTeams
            );
            Game game = plugin.getGameManager().createGame(mode, arena, settings);
            plugin.getGameManager().transition(GameState.LOBBY);
            player.closeInventory();
            Bukkit.broadcastMessage("§6[ManHunt] §e" + factory.getDisplayName()
                    + " §7auf Arena §e" + arena.displayName() + " §7wurde erstellt.");
            Bukkit.broadcastMessage("§7Spieler können mit §e/manhunt menu §7beitreten.");
            player.sendMessage("§aLobby erstellt. Warte auf Spieler.");
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cRunde konnte nicht erstellt werden: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!player.hasPermission("manhunt.admin")) {
            player.closeInventory();
            player.sendMessage("§cDafür hast du keine Berechtigung.");
            return;
        }
        int slot = event.getRawSlot();
        if ((slot == 49 && holder.screen != Screen.TEAM_ASSIGNMENTS) || (holder.screen == Screen.SETTINGS && slot == 26)) {
            player.closeInventory();
            return;
        }
        if ((holder.screen == Screen.OVERVIEW && slot == 24)
                || (holder.screen == Screen.MODES && slot == 45)) {
            plugin.getArenaManagementMenu().open(player);
            return;
        }
        if (holder.screen == Screen.OVERVIEW && slot == 26) {
            openTeamAssignments(player);
            return;
        }
        if (holder.screen == Screen.TEAM_ASSIGNMENTS) {
            Game active = plugin.getGameManager().getActiveGame().orElse(null);
            if (active == null || (active.getState() != GameState.WAITING && active.getState() != GameState.LOBBY
                    && active.getState() != GameState.COUNTDOWN)) {
                player.sendMessage("§cDie Teamverwaltung ist nur vor Spielbeginn verfügbar.");
                open(player);
                return;
            }
            if (slot == 49) { openOverview(player); return; }
            String id = holder.entries.get(slot);
            if (id != null) {
                UUID playerId = UUID.fromString(id);
                List<String> keys = teamKeys(active.getMode().getId());
                String current = active.getLobbyTeamPreference(playerId).orElse("");
                int index = keys.indexOf(current);
                String next = index + 1 >= keys.size() ? "" : keys.get(index + 1);
                active.setLobbyTeamPreference(playerId, next.isEmpty() ? null : next);
                openTeamAssignments(player);
            }
            return;
        }
        if (holder.screen == Screen.OVERVIEW && slot == 22) {
            Game active = plugin.getGameManager().getActiveGame().orElse(null);
            if (active == null) {
                openModes(player);
            } else if (active.getState() == GameState.COUNTDOWN) {
                Bukkit.dispatchCommand(player, "manhunt game cancel");
                open(player);
            } else if (active.getState() == GameState.LOBBY || active.getState() == GameState.WAITING) {
                Bukkit.dispatchCommand(player, "manhunt game start");
                open(player);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (holder.screen == Screen.OVERVIEW && slot == 20) {
            player.closeInventory();
            Game active = plugin.getGameManager().getActiveGame().orElse(null);
            if (active == null) {
                openModes(player);
            } else if (active.getState() == GameState.ENDING || active.getState() == GameState.RESETTING) {
                Bukkit.dispatchCommand(player, "manhunt game reset");
                open(player);
            } else {
                Bukkit.dispatchCommand(player, "manhunt game stop Admin-Steuerung");
                open(player);
            }
            return;
        }
        if (holder.screen == Screen.ARENAS && slot == 45) {
            openModes(player);
            return;
        }
        if (holder.screen == Screen.SETTINGS) {
            if (slot == 18) {
                openArenas(player, holder.modeId);
                return;
            }
            if (slot == 11 || slot == 15) {
                int minimum = minimumFor(holder.modeId);
                int updated = slot == 11 ? Math.max(minimum, holder.maxPlayers - 1)
                        : Math.min(32, holder.maxPlayers + 1);
                openSettings(player, holder.modeId, holder.arenaId, updated, holder.randomTeams);
                return;
            }
            if (slot == 17) {
                openSettings(player, holder.modeId, holder.arenaId, holder.maxPlayers, !holder.randomTeams);
                return;
            }
            if (slot == 22) {
                createGame(player, holder.modeId, holder.arenaId, holder.maxPlayers, holder.randomTeams);
                return;
            }
        }
        String selected = holder.entries.get(slot);
        if (selected == null) return;
        if (holder.screen == Screen.MODES) {
            openArenas(player, selected);
        } else if (holder.screen == Screen.ARENAS) {
            int defaultMax = plugin.getConfigManager().getInt("game.max-players", 8);
            openSettings(player, holder.modeId, selected, defaultMax);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private int minimumFor(String modeId) {
        int configured = plugin.getConfigManager().getInt("game.min-players", 2);
        return plugin.getGameModeRegistry().get(modeId)
                .map(factory -> Math.max(configured, factory.createSession().getMinimumPlayers()))
                .orElse(configured);
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

    private void openTeamAssignments(Player player) {
        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) { openModes(player); return; }
        MenuHolder holder = new MenuHolder(Screen.TEAM_ASSIGNMENTS, game.getMode().getId(), null, 0);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ManHunt • Lobby-Teams"));
        holder.inventory = inventory;
        inventory.setItem(4, item(Material.SHIELD, "§bTeamzuweisung",
                "§7Spieler anklicken, um ihre Wunschrolle zu wechseln.",
                "§7Die festen Teamgrößen des Modus bleiben erhalten.",
                "§7Nicht zugewiesene Spieler werden automatisch verteilt."));
        int slot = 9;
        for (GamePlayer participant : game.getPlayers()) {
            if (slot > 44) break;
            UUID id = participant.getUniqueId();
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name == null) name = id.toString().substring(0, 8);
            String selected = game.getLobbyTeamPreference(id).orElse("");
            holder.entries.put(slot, id.toString());
            inventory.setItem(slot++, item(Material.PLAYER_HEAD, "§e" + name,
                    "§7Zuweisung: §f" + teamLabel(selected),
                    "§eKlicken zum Wechseln"));
        }
        inventory.setItem(49, item(Material.ARROW, "§eZurück", "§7Zur Rundenverwaltung"));
        player.openInventory(inventory);
    }

    private List<String> teamKeys(String modeId) {
        return switch (modeId.toLowerCase(java.util.Locale.ROOT)) {
            case "hitman" -> List.of("", "hitmen", "survivors");
            case "juggernaut" -> List.of("", "juggernaut", "hunters");
            case "infection" -> List.of("", "infected", "survivors");
            default -> List.of("");
        };
    }

    private String teamLabel(String key) {
        return switch (key) {
            case "hitmen" -> "Hitman";
            case "survivors" -> "Überlebende";
            case "juggernaut" -> "Juggernaut";
            case "hunters" -> "Jäger";
            case "infected" -> "Infizierte";
            default -> "Automatisch";
        };
    }

    private enum Screen { OVERVIEW, MODES, ARENAS, SETTINGS, TEAM_ASSIGNMENTS }

    private static final class MenuHolder implements InventoryHolder {
        private final Screen screen;
        private final String modeId;
        private final String arenaId;
        private final int maxPlayers;
        private final boolean randomTeams;
        private final Map<Integer, String> entries = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(Screen screen, String modeId, String arenaId, int maxPlayers) {
            this(screen, modeId, arenaId, maxPlayers, true);
        }

        private MenuHolder(Screen screen, String modeId, String arenaId, int maxPlayers, boolean randomTeams) {
            this.screen = screen;
            this.modeId = modeId;
            this.arenaId = arenaId;
            this.maxPlayers = maxPlayers;
            this.randomTeams = randomTeams;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
