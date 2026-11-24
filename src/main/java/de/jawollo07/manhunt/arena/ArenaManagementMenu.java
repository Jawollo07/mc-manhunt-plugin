package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-game arena and template administration. All actions are permission checked and non-destructive to templates. */
public final class ArenaManagementMenu implements Listener {
    private static final int SIZE = 54;
    private final ManHuntPlugin plugin;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public ArenaManagementMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!allowed(player)) return;
        openArenaList(player);
    }

    private void openArenaList(Player player) {
        MenuHolder holder = new MenuHolder(Screen.ARENAS, null, null);
        Inventory inventory = inventory(holder, "ManHunt • Arenen");
        try {
            List<Arena> arenas = plugin.getArenaService().findAll();
            int slot = 10;
            for (Arena arena : arenas) {
                if (slot > 43) break;
                holder.entries.put(slot, arena.id());
                var template = plugin.getMapManager().inspectTemplate(arena.worldName());
                Material icon = !template.valid() ? Material.BARRIER
                        : template.loaded() ? Material.YELLOW_CONCRETE : Material.MAP;
                inventory.setItem(slot++, item(icon, "§e" + arena.displayName(),
                        "§7ID: §f" + arena.id(),
                        "§7Vorlage: §f" + arena.worldName(),
                        "§7Vorlagenstatus: " + statusColor(template) + template.status(),
                        "§7Spawnpunkte: §f" + arena.spawnPoints().size(),
                        "§aKlicken für Details"));
            }
            if (arenas.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "§cNoch keine Arenen",
                        "§7Öffne §eKartenvorlagen§7 und wähle eine Welt aus,",
                        "§7um daraus deine erste Arena anzulegen."));
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not list arenas: " + exception.getMessage());
            inventory.setItem(22, item(Material.BARRIER, "§cArenen konnten nicht geladen werden"));
        }
        inventory.setItem(45, item(Material.MAP, "§aKartenvorlagen",
                "§7Vorhandene Minecraft-Welten durchsuchen"));
        inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openTemplates(Player player) {
        MenuHolder holder = new MenuHolder(Screen.TEMPLATES, null, null);
        Inventory inventory = inventory(holder, "ManHunt • Kartenvorlagen");
        try {
            List<de.jawollo07.manhunt.map.MapTemplate> templates = plugin.getMapManager().listTemplates();
            int slot = 10;
            for (var template : templates) {
                if (slot > 43) break;
                holder.entries.put(slot, template.id());
                Material icon = !template.valid() ? Material.BARRIER
                        : template.loaded() ? Material.YELLOW_CONCRETE : Material.GRASS_BLOCK;
                inventory.setItem(slot++, item(icon, (template.valid() ? "§a" : "§c") + template.id(),
                        "§7Status: " + statusColor(template) + template.status(),
                        template.valid() ? "§7Als Arena registrieren/öffnen" : "§cNicht als Vorlage verwendbar"));
            }
            if (templates.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "§cKeine Weltordner gefunden",
                    "§7Lege eine Minecraft-Welt in den Server-Weltordner."));
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not scan map templates: " + exception.getMessage());
            inventory.setItem(22, item(Material.BARRIER, "§cVorlagen konnten nicht gelesen werden"));
        }
        inventory.setItem(45, item(Material.ARROW, "§eZurück zu den Arenen"));
        inventory.setItem(49, item(Material.SUNFLOWER, "§eErneut scannen"));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openDetails(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElse(null);
            if (arena == null) {
                player.sendMessage("§cDiese Arena existiert nicht mehr.");
                openArenaList(player);
                return;
            }
            var template = plugin.getMapManager().inspectTemplate(arena.worldName());
            MenuHolder holder = new MenuHolder(Screen.DETAILS, arena.id(), null);
            Inventory inventory = inventory(holder, "ManHunt • " + arena.displayName());
            inventory.setItem(4, item(template.valid() ? Material.MAP : Material.BARRIER,
                    "§e" + arena.displayName(),
                    "§7Arena-ID: §f" + arena.id(),
                    "§7Vorlage: §f" + arena.worldName(),
                    "§7Status: " + statusColor(template) + template.status(),
                    "§7Spawnpunkte: §f" + arena.spawnPoints().size()));
            String[] spawnNames = {
                    "default", "team:hitmen", "team:survivors", "team:juggernaut",
                    "team:hunters", "team:assassins", "team:infected", "extraction"
            };
            Material[] spawnIcons = {
                    Material.COMPASS, Material.IRON_SWORD, Material.SHIELD, Material.NETHERITE_CHESTPLATE,
                    Material.BOW, Material.DIAMOND_SWORD, Material.ROTTEN_FLESH, Material.ENDER_EYE
            };
            for (int i = 0; i < spawnNames.length; i++) {
                String name = spawnNames[i];
                boolean configured = arena.spawnPoint(name).isPresent();
                inventory.setItem(10 + i, item(configured ? Material.LIME_DYE : spawnIcons[i],
                        (configured ? "§a" : "§e") + name,
                        configured ? "§7Gespeichert – klicken zum Überschreiben" : "§7Noch nicht gesetzt",
                        "§7Du musst dich in der Vorlagenwelt befinden."));
                holder.spawnActions.put(10 + i, name);
            }
            int slot = 28;
            for (ArenaSpawnPoint point : arena.spawnPoints().values()) {
                if (slot > 43) break;
                holder.spawnEntries.put(slot, point.name());
                inventory.setItem(slot++, item(Material.ARMOR_STAND, "§b" + point.name(),
                        String.format(Locale.ROOT, "§7X: §f%.2f §7Y: §f%.2f §7Z: §f%.2f",
                                point.x(), point.y(), point.z()),
                        "§eLinksklick: Spawnpunkt entfernen",
                        "§eRechtsklick: Position in Vorlagenwelt testen"));
            }
            inventory.setItem(45, item(Material.ARROW, "§eZurück"));
            inventory.setItem(47, item(Material.NAME_TAG, "§eAnzeigename ändern",
                    "§7Neuen Namen im Chat eingeben."));
            inventory.setItem(48, item(Material.FILLED_MAP, "§eKartenvorlage wechseln",
                    "§7Neue Vorlagen-ID im Chat eingeben.",
                    "§cSpawnpunkte werden dabei zurückgesetzt."));
            inventory.setItem(49, item(Material.TNT, "§cArena löschen",
                    "§7Entfernt nur den Arena-Eintrag,",
                    "§7niemals den Vorlagen-Weltordner."));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cArena-Details konnten nicht geladen werden: " + exception.getMessage());
            openArenaList(player);
        }
    }

    private void openConfirm(Player player, String arenaId, String spawnName) {
        MenuHolder holder = new MenuHolder(Screen.CONFIRM, arenaId, spawnName);
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("ManHunt • Löschen bestätigen"));
        holder.inventory = inventory;
        inventory.setItem(13, item(Material.TNT, "§cWirklich löschen?",
                spawnName == null ? "§7Arena: §f" + arenaId : "§7Spawnpunkt: §f" + spawnName,
                "§7Diese Aktion kann nicht rückgängig gemacht werden."));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "§aJa, löschen"));
        inventory.setItem(15, item(Material.RED_CONCRETE, "§cAbbrechen"));
        player.openInventory(inventory);
    }

    private void createFromTemplate(Player player, String templateId) {
        try {
            var template = plugin.getMapManager().inspectTemplate(templateId);
            if (!template.valid()) {
                player.sendMessage("§cDiese Welt ist keine gültige Kartenvorlage: " + template.status());
                return;
            }
            Arena arena = plugin.getArenaService().find(templateId).orElse(null);
            if (arena == null) {
                String displayName = templateId.replace('_', ' ').replace('-', ' ');
                displayName = java.util.Arrays.stream(displayName.split("\\s+"))
                        .filter(part -> !part.isBlank())
                        .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT)
                                + part.substring(1).toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.joining(" "));
                arena = new Arena(templateId, displayName, templateId);
                plugin.getArenaService().save(arena);
                player.sendMessage("§aArena §e" + displayName + " §awurde aus der Vorlage registriert.");
            }
            if (template.loaded()) {
                player.sendMessage("§eHinweis: Die Vorlagenwelt ist aktuell geladen. Vor dem Spielstart muss sie entladen sein.");
            }
            openDetails(player, arena.id());
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cVorlage konnte nicht verwendet werden: " + exception.getMessage());
        }
    }

    private void setSpawn(Player player, String arenaId, String spawnName) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            if (!player.getWorld().getName().equals(arena.worldName())) {
                player.sendMessage("§cGehe zuerst in die Vorlagenwelt §e" + arena.worldName()
                        + " §cund öffne dort erneut die Arenaverwaltung.");
                return;
            }
            var location = player.getLocation();
            ArenaSpawnPoint point = new ArenaSpawnPoint(spawnName, location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), location.getPitch());
            plugin.getArenaService().save(arena.withSpawnPoint(point));
            player.sendMessage("§aSpawnpunkt §e" + spawnName + " §awurde an deiner Position gespeichert.");
            openDetails(player, arenaId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cSpawnpunkt konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private void deleteConfirmed(Player player, MenuHolder holder) {
        try {
            if (holder.secondaryId == null) {
                Game active = plugin.getGameManager().getActiveGame().orElse(null);
                if (active != null && active.getArena().id().equalsIgnoreCase(holder.arenaId)) {
                    player.sendMessage("§cDiese Arena wird gerade in einer Runde verwendet.");
                    openDetails(player, holder.arenaId);
                    return;
                }
                plugin.getArenaService().delete(holder.arenaId);
                player.sendMessage("§aArena-Eintrag wurde gelöscht. Die Weltvorlage blieb unverändert.");
                openArenaList(player);
            } else {
                Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow(
                        () -> new IllegalStateException("Arena nicht gefunden."));
                plugin.getArenaService().save(arena.withoutSpawnPoint(holder.secondaryId));
                player.sendMessage("§aSpawnpunkt §e" + holder.secondaryId + " §awurde entfernt.");
                openDetails(player, holder.arenaId);
            }
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cLöschen fehlgeschlagen: " + exception.getMessage());
            openDetails(player, holder.arenaId);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!allowed(player)) return;
        int slot = event.getRawSlot();

        if (holder.screen == Screen.ARENAS) {
            if (slot == 45) { openTemplates(player); return; }
            if (slot == 49) { openArenaList(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String id = holder.entries.get(slot);
            if (id != null) openDetails(player, id);
        } else if (holder.screen == Screen.TEMPLATES) {
            if (slot == 45) { openArenaList(player); return; }
            if (slot == 49) { openTemplates(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String id = holder.entries.get(slot);
            if (id != null) createFromTemplate(player, id);
        } else if (holder.screen == Screen.DETAILS) {
            if (slot == 45) { openArenaList(player); return; }
            if (slot == 47 || slot == 48) {
                pendingInputs.put(player.getUniqueId(), new PendingInput(holder.arenaId,
                        slot == 47 ? InputAction.RENAME : InputAction.CHANGE_TEMPLATE));
                player.closeInventory();
                player.sendMessage(slot == 47
                        ? "§eGib den neuen Anzeigenamen im Chat ein. Mit "-" abbrechen."
                        : "§eGib die ID der neuen Kartenvorlage im Chat ein. Mit "-" abbrechen.");
                return;
            }
            if (slot == 49) { openConfirm(player, holder.arenaId, null); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String spawnName = holder.spawnActions.get(slot);
            if (spawnName != null) { setSpawn(player, holder.arenaId, spawnName); return; }
            spawnName = holder.spawnEntries.get(slot);
            if (spawnName != null) {
                if (event.getClick().isRightClick()) {
                    try {
                        Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
                        if (!player.getWorld().getName().equals(arena.worldName())) {
                            player.sendMessage("§cZum Testen musst du dich in der Vorlagenwelt befinden.");
                            return;
                        }
                        arena.spawnPoint(spawnName).ifPresent(point -> player.teleport(point.toLocation(player.getWorld())));
                    } catch (SQLException exception) {
                        player.sendMessage("§cSpawnpunkt konnte nicht geladen werden.");
                    }
                } else openConfirm(player, holder.arenaId, spawnName);
            }
        } else if (holder.screen == Screen.CONFIRM) {
            if (slot == 11) deleteConfirmed(player, holder);
            else if (slot == 15) {
                if (holder.secondaryId == null) openDetails(player, holder.arenaId);
                else openDetails(player, holder.arenaId);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(event.getPlayer(), pending, value));
    }

    private void applyInput(Player player, PendingInput pending, String value) {
        if (value.equals("-")) {
            player.sendMessage("§7Änderung abgebrochen.");
            openDetails(player, pending.arenaId());
            return;
        }
        if (value.isBlank() || value.length() > 48) {
            player.sendMessage("§cDer Wert muss zwischen 1 und 48 Zeichen lang sein.");
            openDetails(player, pending.arenaId());
            return;
        }
        try {
            Arena arena = plugin.getArenaService().find(pending.arenaId()).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            if (pending.action() == InputAction.RENAME) {
                plugin.getArenaService().save(new Arena(arena.id(), value, arena.worldName(), arena.spawnPoints()));
                player.sendMessage("§aAnzeigename wurde geändert.");
            } else {
                var template = plugin.getMapManager().inspectTemplate(value);
                if (!template.valid()) throw new IllegalArgumentException("Keine gültige Vorlage: " + template.status());
                plugin.getArenaService().save(new Arena(arena.id(), arena.displayName(), value));
                player.sendMessage("§aKartenvorlage geändert. §eAlle bisherigen Spawnpunkte wurden zurückgesetzt, da sie zur alten Karte gehörten.");
                if (template.loaded()) player.sendMessage("§eDie neue Vorlagenwelt muss vor Spielstart entladen werden.");
            }
            openDetails(player, pending.arenaId());
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cÄnderung fehlgeschlagen: " + exception.getMessage());
            openDetails(player, pending.arenaId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private boolean allowed(Player player) {
        if (!player.hasPermission("manhunt.admin")) {
            player.closeInventory();
            player.sendMessage("§cDafür hast du keine Berechtigung.");
            return false;
        }
        return true;
    }

    private Inventory inventory(MenuHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(title));
        holder.inventory = inventory;
        return inventory;
    }

    private String statusColor(de.jawollo07.manhunt.map.MapTemplate template) {
        if (!template.valid()) return "§c";
        return template.loaded() ? "§e" : "§a";
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

    private enum Screen { ARENAS, TEMPLATES, DETAILS, CONFIRM }
    private enum InputAction { RENAME, CHANGE_TEMPLATE }
    private record PendingInput(String arenaId, InputAction action) { }

    private static final class MenuHolder implements InventoryHolder {
        private final Screen screen;
        private final String arenaId;
        private final String secondaryId;
        private final Map<Integer, String> entries = new HashMap<>();
        private final Map<Integer, String> spawnActions = new HashMap<>();
        private final Map<Integer, String> spawnEntries = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(Screen screen, String arenaId, String secondaryId) {
            this.screen = screen;
            this.arenaId = arenaId;
            this.secondaryId = secondaryId;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
