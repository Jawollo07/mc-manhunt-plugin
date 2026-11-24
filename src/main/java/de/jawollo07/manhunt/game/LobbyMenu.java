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
    private static final int SIZE = 27;
    private static final int ACTION_SLOT = 22;
    private static final int REFRESH_SLOT = 26;
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
            inventory.setItem(22, item(viewer.hasPermission("manhunt.admin") ? Material.EMERALD : Material.BARRIER,
                    viewer.hasPermission("manhunt.admin") ? "§a§lRunde erstellen" : "§cNoch keine Runde",
                    viewer.hasPermission("manhunt.admin") ? "§7Öffnet die Admin-Auswahl für Modus und Arena."
                            : "§7Sobald ein Admin eine Runde erstellt,",
                    viewer.hasPermission("manhunt.admin") ? "§eKlicken zum Erstellen" : "§7kannst du hier direkt beitreten."));
            inventory.setItem(26, item(Material.CLOCK, "§eAktualisieren", "§7Lobby neu laden"));
            return;
        }

        inventory.setItem(2, item(Material.CLOCK, "§6" + stateName(game.getState()),
                "§7Status: §f" + game.getState(),
                game.getState() == GameState.COUNTDOWN
                        ? "§7Der Start-Countdown läuft."
                        : "§7Warte auf den Spielstart."));
        inventory.setItem(4, item(Material.COMPASS, "§e" + game.getMode().getDisplayName(),
                "§7Spielmodus: §f" + game.getMode().getId(),
                "§7Arena: §f" + game.getArena().displayName()));
        inventory.setItem(6, item(Material.PLAYER_HEAD, "§aSpieler: §f" + game.getPlayerCount()
                        + "§7/§f" + game.getSettings().maxPlayers(),
                "§7Mindestens benötigt: §f" + game.getSettings().minPlayers(),
                "§7Freie Plätze: §f" + game.getAvailableSlots()));

        int slot = 9;
        for (GamePlayer participant : game.getPlayers()) {
            if (slot > 17) break;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(participant.getUniqueId());
            String name = offline.getName();
            if (name == null) name = participant.getUniqueId().toString().substring(0, 8);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offline);
                meta.setDisplayName("§a" + name);
                List<String> lore = new ArrayList<>();
                lore.add(offline.isOnline() ? "§aOnline" : "§7Offline");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
        }
        while (slot <= 17) inventory.setItem(slot++, item(Material.GRAY_STAINED_GLASS_PANE, "§8Freier Platz"));

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
        inventory.setItem(24, item(Material.MAP, "§eArena: " + game.getArena().displayName(),
                "§7Die Arena wird vom Admin festgelegt."));
        if (viewer.hasPermission("manhunt.admin")) {
            inventory.setItem(20, item(Material.COMMAND_BLOCK, "§cAdmin-Menü",
                    "§7Runde verwalten oder eine neue Runde vorbereiten."));
        }
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "§eAktualisieren",
                "§7Spielerliste und Status neu laden."));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        int slot = event.getRawSlot();
        if (slot == 20 && player.hasPermission("manhunt.admin")) {
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
