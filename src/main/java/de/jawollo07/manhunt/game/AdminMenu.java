package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.arena.Arena;
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
        MenuHolder holder = new MenuHolder(Screen.OVERVIEW, null);
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
        if (game.getState() == GameState.ENDING || game.getState() == GameState.RESETTING) {
            inventory.setItem(20, item(Material.CLOCK, "§eReset / Aufräumen",
                    "§7Stellt Spieler wieder her und entfernt die temporäre Map."));
        } else {
            inventory.setItem(20, item(Material.REDSTONE_BLOCK, "§cRunde stoppen",
                    "§7Beendet die aktuelle Runde und stellt Spieler wieder her."));
        }
        inventory.setItem(22, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openModes(Player player) {
        MenuHolder holder = new MenuHolder(Screen.MODES, null);
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
        inventory.setItem(49, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openArenas(Player player, String modeId) {
        MenuHolder holder = new MenuHolder(Screen.ARENAS, modeId);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ManHunt • Arena wählen"));
        holder.inventory = inventory;
        try {
            List<Arena> arenas = plugin.getArenaService().findAll();
            int slot = FIRST_SLOT;
            for (Arena arena : arenas) {
                if (slot > LAST_SLOT) break;
                holder.entries.put(slot, arena.id());
                inventory.setItem(slot++, item(Material.MAP, "§a" + arena.displayName(),
                        "§7Arena-ID: §f" + arena.id(),
                        "§7Vorlage: §f" + arena.worldName(),
                        "§aKlicken, um die Runde zu erstellen"));
            }
            if (arenas.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "§cKeine Arenen vorhanden",
                    "§7Lege zuerst eine Arena mit /manhunt arena create an."));
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load arenas for admin menu: " + exception.getMessage());
            inventory.setItem(22, item(Material.BARRIER, "§cArenen konnten nicht geladen werden"));
        }
        inventory.setItem(45, item(Material.ARROW, "§eZurück zur Modusauswahl"));
        inventory.setItem(49, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void createGame(Player player, String modeId, String arenaId) {
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
            GameSettings settings = new GameSettings(
                    plugin.getConfigManager().getInt("game.min-players", 2),
                    plugin.getConfigManager().getInt("game.max-players", 8),
                    plugin.getConfigManager().getInt("game.countdown-seconds", 10),
                    plugin.getConfigManager().getInt("game.time-limit-seconds", 2400),
                    plugin.getConfigManager().getBoolean("game.allow-late-join", false),
                    plugin.getConfigManager().getBoolean("game.allow-spectators", true)
            );
            Game game = plugin.getGameManager().createGame(factory.createSession(), arena, settings);
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
        if (slot == 49 || (holder.screen == Screen.OVERVIEW && slot == 22)) {
            player.closeInventory();
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
        String selected = holder.entries.get(slot);
        if (selected == null) return;
        if (holder.screen == Screen.MODES) {
            openArenas(player, selected);
        } else {
            createGame(player, holder.modeId, selected);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
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

    private enum Screen { OVERVIEW, MODES, ARENAS }

    private static final class MenuHolder implements InventoryHolder {
        private final Screen screen;
        private final String modeId;
        private final Map<Integer, String> entries = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(Screen screen, String modeId) {
            this.screen = screen;
            this.modeId = modeId;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
