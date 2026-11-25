package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private final Map<UUID, Location> editingReturnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, String> editingTemplateIds = new ConcurrentHashMap<>();

    public ArenaManagementMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!allowed(player)) return;
        openArenaList(player);
    }

    private void openArenaList(Player player) { openArenaList(player, 0); }

    private void openArenaList(Player player, int page) {
        MenuHolder holder = new MenuHolder(Screen.ARENAS, null, null, page);
        Inventory inventory = inventory(holder, "ManHunt • Arenen (" + (page + 1) + ")");
        try {
            List<Arena> arenas = plugin.getArenaService().findAll().stream()
                    .sorted(java.util.Comparator.comparing(Arena::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
            int pageCount = Math.max(1, (arenas.size() + 33) / 34);
            int start = Math.min(page, pageCount - 1) * 34;
            holder.page = Math.min(page, pageCount - 1);
            int slot = 10;
            for (Arena arena : arenas.subList(start, Math.min(start + 34, arenas.size()))) {
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
        inventory.setItem(46, item(Material.WRITABLE_BOOK, "§aNeue Arena erstellen",
                "§7Wähle eine Kartenvorlage aus.",
                "§7Die Arena wird automatisch angelegt."));
        if (editingReturnLocations.containsKey(player.getUniqueId())) {
            inventory.setItem(47, item(Material.OAK_DOOR, "§eVorlage verlassen",
                    "§7Zurück zum vorherigen Standort", "§7und die Vorlage sicher entladen."));
        }
        if (holder.page > 0) inventory.setItem(48, item(Material.ARROW, "§eVorherige Seite"));
        inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
        if (holder.page + 1 < Math.max(1, (countArenas() + 33) / 34)) inventory.setItem(50, item(Material.ARROW, "§eNächste Seite"));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private void openTemplates(Player player) { openTemplates(player, 0); }

    private void openTemplates(Player player, int page) {
        MenuHolder holder = new MenuHolder(Screen.TEMPLATES, null, null, page);
        Inventory inventory = inventory(holder, "ManHunt • Kartenvorlagen (" + (page + 1) + ")");
        try {
            List<de.jawollo07.manhunt.map.MapTemplate> templates = plugin.getMapManager().listTemplates().stream()
                    .sorted(java.util.Comparator.comparing(de.jawollo07.manhunt.map.MapTemplate::id, String.CASE_INSENSITIVE_ORDER)).toList();
            int pageCount = Math.max(1, (templates.size() + 33) / 34);
            int start = Math.min(page, pageCount - 1) * 34;
            holder.page = Math.min(page, pageCount - 1);
            int slot = 10;
            for (var template : templates.subList(start, Math.min(start + 34, templates.size()))) {
                holder.entries.put(slot, template.id());
                Material icon = !template.valid() ? Material.BARRIER
                        : template.loaded() ? Material.YELLOW_CONCRETE : Material.GRASS_BLOCK;
                inventory.setItem(slot++, item(icon, (template.valid() ? "§a" : "§c") + template.id(),
                        "§7Status: " + statusColor(template) + template.status(),
                        "§7Letzte Änderung: §f" + template.lastModified(),
                        "§7Ordner: §f" + template.folder(),
                        template.valid() ? "§aKlicken für Details & Arena-Erstellung" : "§cNicht als Vorlage verwendbar"));
            }
            if (templates.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "§cKeine Weltordner gefunden",
                    "§7Lege eine Minecraft-Welt in den Server-Weltordner."));
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not scan map templates: " + exception.getMessage());
            inventory.setItem(22, item(Material.BARRIER, "§cVorlagen konnten nicht gelesen werden"));
        }
        inventory.setItem(45, item(Material.ARROW, "§eZurück zu den Arenen"));
        if (editingReturnLocations.containsKey(player.getUniqueId())) {
            inventory.setItem(47, item(Material.OAK_DOOR, "§eVorlage verlassen",
                    "§7Zurück zum vorherigen Standort", "§7und die Vorlage sicher entladen."));
        }
        if (holder.page > 0) inventory.setItem(48, item(Material.ARROW, "§eVorherige Seite"));
        inventory.setItem(49, item(Material.SUNFLOWER, "§eErneut scannen"));
        if (holder.page + 1 < Math.max(1, (countTemplates() + 33) / 34)) inventory.setItem(50, item(Material.ARROW, "§eNächste Seite"));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    private int countArenas() {
        try { return plugin.getArenaService().findAll().size(); }
        catch (SQLException exception) { return 0; }
    }

    private int countTemplates() {
        try { return plugin.getMapManager().listTemplates().size(); }
        catch (IOException | IllegalArgumentException exception) { return 0; }
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
            String[] spawnLabels = {
                    "Standard-Spawn", "Hitmen", "Überlebende", "Juggernaut",
                    "Jäger", "Assassinen", "Infizierte", "Evakuierung"
            };
            Material[] spawnIcons = {
                    Material.COMPASS, Material.IRON_SWORD, Material.SHIELD, Material.NETHERITE_CHESTPLATE,
                    Material.BOW, Material.DIAMOND_SWORD, Material.ROTTEN_FLESH, Material.ENDER_EYE
            };
            for (int i = 0; i < spawnNames.length; i++) {
                String name = spawnNames[i];
                int count = spawnCount(arena, name);
                boolean configured = count > 0;
                String action = name.startsWith("team:")
                        ? "§eKlicken: Nächsten Team-Spawn hier hinzufügen"
                        : configured ? "§7Klicken: Position überschreiben" : "§7Klicken: Position hier setzen";
                inventory.setItem(10 + i, item(configured ? Material.LIME_DYE : spawnIcons[i],
                        (configured ? "§a" : "§e") + spawnLabels[i],
                        name.startsWith("team:") ? "§7Gespeicherte Positionen: §f" + count : (configured ? "§7Gespeichert" : "§7Noch nicht gesetzt"),
                        action,
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
            inventory.setItem(46, item(Material.WRITABLE_BOOK, "§aArena duplizieren",
                    "§7Kopiert diese Arena inklusive",
                    "§7Kartenvorlage und aller Spawnpunkte.",
                    "§7Die Kopie erhält eine eigene Arena-ID."));
            inventory.setItem(47, item(Material.NAME_TAG, "§eAnzeigename ändern",
                    "§7Neuen Namen im Chat eingeben."));
            inventory.setItem(48, item(Material.FILLED_MAP, "§eKartenvorlage wechseln",
                    "§7Neue Vorlagen-ID im Chat eingeben.",
                    "§cSpawnpunkte werden dabei zurückgesetzt."));
            inventory.setItem(49, item(Material.TNT, "§cArena löschen",
                    "§7Entfernt nur den Arena-Eintrag,",
                    "§7niemals den Vorlagen-Weltordner."));
            inventory.setItem(50, item(Material.ENDER_PEARL, "§aVorlagenwelt betreten",
                    "§7Lädt die Kartenvorlage bei Bedarf.",
                    "§7Teleportiert dich direkt zum Weltspawn.",
                    "§7Danach kannst du Spawnpunkte mit einem Klick setzen."));
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

    private void openTemplateDetails(Player player, String templateId) {
        try {
            var template = plugin.getMapManager().inspectTemplate(templateId);
            List<Arena> linkedArenas = plugin.getArenaService().findAll().stream()
                    .filter(arena -> arena.worldName().equalsIgnoreCase(templateId))
                    .toList();
            MenuHolder holder = new MenuHolder(Screen.TEMPLATE_DETAILS, templateId, null);
            Inventory inventory = inventory(holder, "ManHunt • Vorlage " + templateId);
            inventory.setItem(4, item(template.valid() ? Material.GRASS_BLOCK : Material.BARRIER,
                    "§e" + templateId,
                    "§7Status: " + statusColor(template) + template.status(),
                    "§7Zuletzt geändert: §f" + template.lastModified(),
                    "§7Weltordner: §f" + template.folder(),
                    "§7Verknüpfte Arenen: §f" + linkedArenas.size(),
                    linkedArenas.isEmpty() ? "§eNoch keiner Arena zugeordnet" : "§7Arenen: §f"
                            + linkedArenas.stream().map(Arena::id).limit(4)
                            .collect(java.util.stream.Collectors.joining(", ")),
                    "§7Original bleibt unverändert; Runden nutzen temporäre Kopien."));
            inventory.setItem(20, item(template.loaded() ? Material.ENDER_PEARL : Material.COMPASS,
                    template.loaded() ? "§aWelt betreten" : "§aVorlage laden & betreten",
                    "§7Lädt die Originalwelt, falls nötig.",
                    "§7Teleportiert dich zum Weltspawn.",
                    "§7Danach kannst du Spawnpunkte setzen."));
            boolean alreadyRegistered = linkedArenas.stream().anyMatch(arena -> arena.worldName().equalsIgnoreCase(templateId)
                    || (templateId.startsWith("minecraft:") && arena.worldName().equalsIgnoreCase(templateId.substring("minecraft:".length()))));
            inventory.setItem(22, item(Material.CHEST, alreadyRegistered ? "§aVorhandene Arena öffnen" : "§aArena aus Vorlage erstellen",
                    "§7" + (alreadyRegistered ? "Öffnet die bereits verknüpfte Arena." : "Legt automatisch eine Arena an."),
                    "§7Name und ID werden aus der Vorlage abgeleitet.",
                    "§7Die Originalwelt bleibt unverändert.",
                    "§7Danach kannst du Spawnpunkte setzen."));
            if (plugin.getMapManager().isLoadedForEditing(templateId)) {
                inventory.setItem(24, item(Material.REDSTONE_BLOCK, "§cVorlage entladen",
                        "§7Nur möglich, wenn sich keine Spieler",
                        "§7mehr in dieser Welt befinden."));
            } else if (template.loaded()) {
                inventory.setItem(24, item(Material.GRAY_DYE, "§7Bereits anderweitig geladen",
                        "§7Diese Welt wurde nicht von der",
                        "§7ManHunt-Vorlagenbearbeitung geladen."));
            }
            inventory.setItem(45, item(Material.ARROW, "§eZurück"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cVorlage konnte nicht geöffnet werden: " + exception.getMessage());
            openTemplates(player);
        }
    }

    /** Loads a template and teleports an administrator into it for editing. */
    public void enterTemplateForEditing(Player player, String templateId) {
        if (!allowed(player)) return;
        try {
            String currentTemplate = editingTemplateIds.get(player.getUniqueId());
            if (currentTemplate != null && !currentTemplate.equalsIgnoreCase(templateId)) {
                player.sendMessage("§cDu bearbeitest bereits §e" + currentTemplate
                        + "§c. Verlasse diese Vorlage zuerst, bevor du eine andere öffnest.");
                return;
            }
            World world = plugin.getMapManager().loadTemplateForEditing(templateId);
            UUID playerId = player.getUniqueId();
            if (!player.getWorld().equals(world)) {
                editingReturnLocations.putIfAbsent(playerId, player.getLocation().clone());
                if (!player.teleport(world.getSpawnLocation())) {
                    editingReturnLocations.remove(playerId);
                    try {
                        plugin.getMapManager().unloadTemplateAfterEditing(templateId);
                    } catch (IllegalStateException ignored) {
                        // Another editor may already be inside this template.
                    }
                    player.sendMessage("§cTeleport in die Vorlagenwelt fehlgeschlagen.");
                    return;
                }
            } else if (!editingReturnLocations.containsKey(playerId)) {
                World fallbackWorld = Bukkit.getWorlds().stream()
                        .filter(candidate -> !candidate.equals(world))
                        .findFirst().orElse(null);
                if (fallbackWorld == null) {
                    try {
                        plugin.getMapManager().unloadTemplateAfterEditing(templateId);
                    } catch (IllegalStateException ignored) {
                        // Keep the world loaded if another player is already inside.
                    }
                    player.sendMessage("§cEs gibt keine andere geladene Welt als Rückkehrziel.");
                    return;
                }
                editingReturnLocations.put(playerId, fallbackWorld.getSpawnLocation());
                player.sendMessage("§eDu warst bereits in der Vorlagenwelt. Als Rückkehrziel wird der Spawn von §f"
                        + fallbackWorld.getName() + " §everwendet.");
            }
            editingTemplateIds.put(playerId, templateId);
            player.closeInventory();
            player.sendMessage("§aDu bearbeitest jetzt die Originalvorlage §e" + templateId + "§a.");
            player.sendMessage("§7Nutze §e/manhunt arenas §7für Spawnpunkte. Mit §eVorlage verlassen §7kehrst du zurück.");
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cVorlage konnte nicht geladen werden: " + exception.getMessage());
        }
    }

    /** Returns the administrator to their previous location, then safely unloads our template world. */
    public void leaveTemplateEditing(Player player) {
        if (!allowed(player)) return;
        Location returnLocation = editingReturnLocations.remove(player.getUniqueId());
        String templateId = editingTemplateIds.remove(player.getUniqueId());
        if (returnLocation == null || templateId == null) {
            player.sendMessage("§eFür dich ist keine aktive Vorlagenbearbeitung gespeichert.");
            return;
        }
        if (!player.teleport(returnLocation)) {
            editingReturnLocations.put(player.getUniqueId(), returnLocation);
            editingTemplateIds.put(player.getUniqueId(), templateId);
            player.sendMessage("§cRückteleport fehlgeschlagen. Du befindest dich weiterhin in der Vorlagenwelt.");
            return;
        }
        player.sendMessage("§aDu hast die Vorlagenwelt verlassen.");
        Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean unloaded = plugin.getMapManager().unloadTemplateAfterEditing(templateId);
                    if (unloaded) player.sendMessage("§aDie Vorlagenwelt wurde gespeichert und entladen.");
                    else player.sendMessage("§7Die Welt war bereits vor der Bearbeitung geladen und bleibt deshalb geladen.");
                } catch (IllegalStateException exception) {
                    player.sendMessage("§eDie Vorlage bleibt geladen, da sich noch andere Spieler darin befinden.");
                }
        });
    }

    private void createFromTemplate(Player player, String templateId) {
        try {
            var template = plugin.getMapManager().inspectTemplate(templateId);
            if (!template.valid()) {
                player.sendMessage("§cDiese Welt ist keine gültige Kartenvorlage: " + template.status());
                return;
            }
            Arena arena = plugin.getArenaService().find(templateId).orElse(null);
            if (arena == null && templateId.startsWith("minecraft:")) {
                String legacyId = templateId.substring("minecraft:".length());
                arena = plugin.getArenaService().findAll().stream()
                        .filter(existing -> existing.worldName().equalsIgnoreCase(legacyId))
                        .findFirst().orElse(null);
            }
            if (arena == null) {
                String displayName = templateId.substring(templateId.lastIndexOf(':') + 1).replace('_', ' ').replace('-', ' ');
                displayName = java.util.Arrays.stream(displayName.split("\\s+"))
                        .filter(part -> !part.isBlank())
                        .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT)
                                + part.substring(1).toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.joining(" "));
                String arenaId = templateId.replace(':', '_').replace('/', '_');
                if (arenaId.length() > 64) arenaId = arenaId.substring(0, 64);
                String baseId = arenaId;
                int suffix = 2;
                while (plugin.getArenaService().find(arenaId).isPresent()) {
                    String tail = "_" + suffix++;
                    arenaId = baseId.substring(0, Math.min(baseId.length(), 64 - tail.length())) + tail;
                }
                arena = new Arena(arenaId, displayName, templateId);
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
            if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
                player.sendMessage("§cGehe zuerst in die Vorlagenwelt §e" + arena.worldName()
                        + " §cund öffne dort erneut die Arenaverwaltung.");
                return;
            }
            var location = player.getLocation();
            String storedName = spawnName;
            if (spawnName.startsWith("team:")) {
                int next = arena.spawnPoints().keySet().stream()
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(spawnName.toLowerCase(Locale.ROOT) + ":"))
                        .map(key -> key.substring(key.lastIndexOf(':') + 1))
                        .filter(part -> part.matches("\\d+"))
                        .mapToInt(Integer::parseInt).max().orElse(0) + 1;
                storedName = spawnName + ":" + next;
            }
            ArenaSpawnPoint point = new ArenaSpawnPoint(storedName, location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), location.getPitch());
            plugin.getArenaService().save(arena.withSpawnPoint(point));
            player.sendMessage("§aSpawnpunkt §e" + storedName + " §awurde an deiner Position gespeichert.");
            openDetails(player, arenaId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cSpawnpunkt konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private static int spawnCount(Arena arena, String spawnName) {
        if (!spawnName.startsWith("team:")) return arena.spawnPoint(spawnName).isPresent() ? 1 : 0;
        String prefix = spawnName.toLowerCase(Locale.ROOT);
        return (int) arena.spawnPoints().keySet().stream()
                .filter(key -> key.equalsIgnoreCase(spawnName)
                        || key.toLowerCase(Locale.ROOT).startsWith(prefix + ":"))
                .count();
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
            if (slot == 48) { openArenaList(player, holder.page - 1); return; }
            if (slot == 50) { openArenaList(player, holder.page + 1); return; }
            if (slot == 45 || slot == 46) { openTemplates(player); return; }
            if (slot == 47 && editingReturnLocations.containsKey(player.getUniqueId())) { leaveTemplateEditing(player); return; }
            if (slot == 49) { openArenaList(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String id = holder.entries.get(slot);
            if (id != null) openDetails(player, id);
        } else if (holder.screen == Screen.TEMPLATES) {
            if (slot == 48) { openTemplates(player, holder.page - 1); return; }
            if (slot == 50) { openTemplates(player, holder.page + 1); return; }
            if (slot == 45) { openArenaList(player); return; }
            if (slot == 47 && editingReturnLocations.containsKey(player.getUniqueId())) { leaveTemplateEditing(player); return; }
            if (slot == 49) { openTemplates(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String id = holder.entries.get(slot);
            if (id != null) openTemplateDetails(player, id);
        } else if (holder.screen == Screen.TEMPLATE_DETAILS) {
            if (slot == 45) { openTemplates(player); return; }
            if (slot == 49) { openTemplateDetails(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            if (slot == 20) { enterTemplateForEditing(player, holder.arenaId); return; }
            if (slot == 22) { createFromTemplate(player, holder.arenaId); return; }
            if (slot == 24) {
                try {
                    boolean unloaded = plugin.getMapManager().unloadTemplateAfterEditing(holder.arenaId);
                    player.sendMessage(unloaded ? "§aVorlage wurde gespeichert und entladen."
                            : "§eDiese Vorlage wurde nicht von der Bearbeitungsfunktion geladen und bleibt geöffnet.");
                    openTemplateDetails(player, holder.arenaId);
                } catch (IllegalStateException exception) {
                    player.sendMessage("§c" + exception.getMessage());
                }
                return;
            }
        } else if (holder.screen == Screen.DETAILS) {
            if (slot == 45) { openArenaList(player); return; }
            if (slot == 46) {
                pendingInputs.put(player.getUniqueId(), new PendingInput(holder.arenaId, InputAction.DUPLICATE));
                player.closeInventory();
                player.sendMessage("§eGib eine neue Arena-ID für die Kopie ein. Mit '-' abbrechen.");
                return;
            }
            if (slot == 47 || slot == 48) {
                pendingInputs.put(player.getUniqueId(), new PendingInput(holder.arenaId,
                        slot == 47 ? InputAction.RENAME : InputAction.CHANGE_TEMPLATE));
                player.closeInventory();
                player.sendMessage(slot == 47
                        ? "§eGib den neuen Anzeigenamen im Chat ein. Mit '-' abbrechen."
                        : "§eGib die ID der neuen Kartenvorlage im Chat ein. Mit '-' abbrechen.");
                return;
            }
            if (slot == 49) { openConfirm(player, holder.arenaId, null); return; }
            if (slot == 50) {
                try {
                    Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
                    enterTemplateForEditing(player, arena.worldName());
                } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
                    player.sendMessage("§cVorlagenwelt konnte nicht geöffnet werden: " + exception.getMessage());
                }
                return;
            }
            if (slot == 53) { player.closeInventory(); return; }
            String spawnName = holder.spawnActions.get(slot);
            if (spawnName != null) { setSpawn(player, holder.arenaId, spawnName); return; }
            spawnName = holder.spawnEntries.get(slot);
            if (spawnName != null) {
                if (event.getClick().isRightClick()) {
                    try {
                        Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
                        if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
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
        if (value.isBlank() || value.length() > 64) {
            player.sendMessage("§cDer Wert muss zwischen 1 und 64 Zeichen lang sein.");
            openDetails(player, pending.arenaId());
            return;
        }
        try {
            Arena arena = plugin.getArenaService().find(pending.arenaId()).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            switch (pending.action()) {
                case RENAME -> {
                    plugin.getArenaService().save(new Arena(arena.id(), value, arena.worldName(), arena.spawnPoints()));
                    player.sendMessage("§aAnzeigename wurde geändert.");
                    openDetails(player, pending.arenaId());
                }
                case CHANGE_TEMPLATE -> {
                    var template = plugin.getMapManager().inspectTemplate(value);
                    if (!template.valid()) throw new IllegalArgumentException("Keine gültige Vorlage: " + template.status());
                    plugin.getArenaService().save(new Arena(arena.id(), arena.displayName(), value));
                    player.sendMessage("§aKartenvorlage geändert. §eAlle bisherigen Spawnpunkte wurden zurückgesetzt, da sie zur alten Karte gehörten.");
                    if (template.loaded()) player.sendMessage("§eDie neue Vorlagenwelt muss vor Spielstart entladen werden.");
                    openDetails(player, pending.arenaId());
                }
                case DUPLICATE -> {
                    Arena copy = plugin.getArenaService().duplicate(arena.id(), value, arena.displayName() + " (Kopie)");
                    player.sendMessage("§aArena §e" + copy.id() + " §awurde mit " + copy.spawnPoints().size()
                            + " Spawnpunkten dupliziert.");
                    openDetails(player, copy.id());
                }
            }
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cÄnderung fehlgeschlagen: " + exception.getMessage());
            openDetails(player, pending.arenaId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        editingReturnLocations.remove(playerId);
        String templateId = editingTemplateIds.remove(playerId);
        if (templateId == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.getMapManager().unloadTemplateAfterEditing(templateId);
            } catch (IllegalStateException exception) {
                plugin.getLogger().info("Keeping template " + templateId
                        + " loaded because another player is still editing it.");
            }
        }, 1L);
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

    private enum Screen { ARENAS, TEMPLATES, TEMPLATE_DETAILS, DETAILS, CONFIRM }
    private enum InputAction { RENAME, CHANGE_TEMPLATE, DUPLICATE }
    private record PendingInput(String arenaId, InputAction action) { }

    private static final class MenuHolder implements InventoryHolder {
        private final Screen screen;
        private final String arenaId;
        private final String secondaryId;
        private final Map<Integer, String> entries = new HashMap<>();
        private final Map<Integer, String> spawnActions = new HashMap<>();
        private final Map<Integer, String> spawnEntries = new HashMap<>();
        private Inventory inventory;
        private int page;

        private MenuHolder(Screen screen, String arenaId, String secondaryId) {
            this(screen, arenaId, secondaryId, 0);
        }

        private MenuHolder(Screen screen, String arenaId, String secondaryId, int page) {
            this.screen = screen;
            this.arenaId = arenaId;
            this.secondaryId = secondaryId;
            this.page = Math.max(0, page);
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
