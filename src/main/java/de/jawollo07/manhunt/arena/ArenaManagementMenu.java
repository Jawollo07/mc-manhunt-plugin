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
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private final Map<UUID, String> setupArenaIds = new ConcurrentHashMap<>();
    private final Map<UUID, StartInventoryData> loadoutEditorBackups = new ConcurrentHashMap<>();

    public ArenaManagementMenu(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!allowed(player)) return;
        String setupArenaId = setupArenaIds.get(player.getUniqueId());
        if (setupArenaId != null) {
            try {
                if (plugin.getArenaService().find(setupArenaId).isPresent()) {
                    openSetup(player, setupArenaId);
                    return;
                }
            } catch (SQLException exception) {
                player.sendMessage("§cGespeicherter Setup-Fortschritt konnte nicht geladen werden.");
            }
            setupArenaIds.remove(player.getUniqueId());
        }
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

    private void openDetails(Player player, String arenaId) { openDetails(player, arenaId, 0); }

    private void openDetails(Player player, String arenaId, int page) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElse(null);
            if (arena == null) {
                player.sendMessage("§cDiese Arena existiert nicht mehr.");
                openArenaList(player);
                return;
            }
            var template = plugin.getMapManager().inspectTemplate(arena.worldName());
            List<ArenaSpawnPoint> spawnPoints = arena.spawnPoints().values().stream()
                    .sorted(java.util.Comparator.comparing(ArenaSpawnPoint::name, String.CASE_INSENSITIVE_ORDER)).toList();
            int spawnPages = Math.max(1, (spawnPoints.size() + 15) / 16);
            int currentPage = Math.max(0, Math.min(page, spawnPages - 1));
            MenuHolder holder = new MenuHolder(Screen.DETAILS, arena.id(), null, currentPage);
            Inventory inventory = inventory(holder, "ManHunt • " + arena.displayName());
            inventory.setItem(4, item(template.valid() ? Material.MAP : Material.BARRIER,
                    "§e" + arena.displayName(),
                    "§7Arena-ID: §f" + arena.id(),
                    "§7Vorlage: §f" + arena.worldName(),
                    "§7Status: " + statusColor(template) + template.status(),
                    "§7Spawnpunkte: §f" + arena.spawnPoints().size(),
                    "§7Freigegebene Modi: §f" + (arena.enabledModeIds().isEmpty() ? "Alle" : String.join(", ", arena.enabledModeIds())),
                    "§7Disconnect-Kulanz: §f" + settingValueLabel("disconnect_grace_seconds",
                            arena.modeSetting("general", "disconnect_grace_seconds", "60"))));
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
            inventory.setItem(22, item(template.valid() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    template.valid() ? "§aVorlage grundsätzlich verwendbar" : "§cVorlage nicht bereit",
                    "§7Gespeicherte Spawnpunkte: §f" + spawnPoints.size(),
                    "§7Team-Spawns: §f" + spawnPoints.stream().filter(point -> point.name().startsWith("team:")).count(),
                    "§7Standard-Spawn: " + (arena.spawnPoint("default").isPresent() ? "§aGesetzt" : "§eNicht gesetzt"),
                    template.loaded() ? "§eVorlage ist geladen – vor Spielstart entladen." : "§aVorlage ist nicht geladen."));
            inventory.setItem(26, item(Material.CLOCK, "§eDisconnect-Kulanz ändern",
                    "§7Aktuell: §f" + settingValueLabel("disconnect_grace_seconds",
                            arena.modeSetting("general", "disconnect_grace_seconds", "60")),
                    "§7Nach Ablauf werden getrennte Spieler",
                    "§7automatisch als ausgeschieden gewertet.",
                    "§eKlicken: Nächsten Wert einstellen"));
            inventory.setItem(27, item(Material.CHEST, "§6Team-Startinventare",
                    "§7Lege für jedes Team eigene Startitems fest.",
                    "§7Items, Rüstung und Schildhand werden gespeichert.",
                    "§eKlicken zum Bearbeiten"));
            int slot = 28;
            for (ArenaSpawnPoint point : spawnPoints.subList(currentPage * 16, Math.min(currentPage * 16 + 16, spawnPoints.size()))) {
                holder.spawnEntries.put(slot, point.name());
                inventory.setItem(slot++, item(Material.ARMOR_STAND, "§b" + point.name(),
                        String.format(Locale.ROOT, "§7X: §f%.2f §7Y: §f%.2f §7Z: §f%.2f",
                                point.x(), point.y(), point.z()),
                        "§eLinksklick: Spawnpunkt entfernen",
                        "§eRechtsklick: Position in Vorlagenwelt testen"));
            }
            inventory.setItem(44, item(Material.BOOK, "§eZurück zum Arena-Setup",
                    "§7Fortschritt ansehen und Einrichtung fortsetzen."));
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
            if (currentPage > 0) inventory.setItem(51, item(Material.ARROW, "§eVorherige Spawnpunkt-Seite"));
            if (currentPage + 1 < spawnPages) inventory.setItem(52, item(Material.ARROW, "§eNächste Spawnpunkt-Seite"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cArena-Details konnten nicht geladen werden: " + exception.getMessage());
            openArenaList(player);
        }
    }

    private void openTeamLoadouts(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow();
            MenuHolder holder = new MenuHolder(Screen.LOADOUT_TEAMS, arenaId, null);
            Inventory inventory = inventory(holder, "ManHunt • Team-Loadouts");
            String[][] teams = {
                    {"assassins", "Assassinen", "DIAMOND_SWORD"},
                    {"hitmen", "Hitmen", "IRON_SWORD"},
                    {"survivors", "Überlebende", "SHIELD"},
                    {"juggernaut", "Juggernaut", "NETHERITE_CHESTPLATE"},
                    {"hunters", "Jäger", "BOW"},
                    {"infected", "Infizierte", "ROTTEN_FLESH"}
            };
            int[] slots = {10, 11, 12, 14, 15, 16};
            for (int i = 0; i < teams.length; i++) {
                String key = teams[i][0];
                boolean configured = !arena.modeSetting("loadouts", key, "").isBlank();
                holder.entries.put(slots[i], key);
                inventory.setItem(slots[i], item(Material.valueOf(teams[i][2]),
                        (configured ? "§a" : "§e") + teams[i][1],
                        "§7Startinventar: " + (configured ? "§aEingerichtet" : "§cStandard"),
                        "§eKlicken zum Bearbeiten"));
            }
            inventory.setItem(4, item(Material.CHEST, "§6Startinventare pro Team",
                    "§7Wähle ein Team aus und bearbeite",
                    "§7dessen Items, Rüstung und Schildhand."));
            inventory.setItem(45, item(Material.ARROW, "§eZurück zu den Arena-Details"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cTeam-Startinventare konnten nicht geladen werden: " + exception.getMessage());
            openDetails(player, arenaId);
        }
    }

    private void openLoadoutEditor(Player player, String arenaId, String teamKey) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow();
            MenuHolder holder = new MenuHolder(Screen.LOADOUT_EDITOR, arenaId, teamKey);
            Inventory inventory = inventory(holder, "Startinventar • " + teamKey);
            String encoded = arena.modeSetting("loadouts", teamKey, "");
            if (!encoded.isBlank()) {
                StartInventoryData data = StartInventoryData.decode(encoded);
                ItemStack[] contents = data.contents();
                ItemStack[] armor = data.armor();
                ItemStack[] extra = data.extra();
                for (int i = 0; i < Math.min(36, contents.length); i++) inventory.setItem(i, contents[i]);
                for (int i = 0; i < Math.min(4, armor.length); i++) inventory.setItem(36 + i, armor[i]);
                if (extra.length > 0) inventory.setItem(40, extra[0]);
            }
            inventory.setItem(41, item(Material.IRON_HELMET, "§eRüstung",
                    "§7Slots 36–39: Schuhe bis Helm"));
            inventory.setItem(42, item(Material.SHIELD, "§eSchildhand",
                    "§7Slot 40: Schildhand / Offhand"));
            inventory.setItem(45, item(Material.LIME_CONCRETE, "§aStartinventar speichern",
                    "§7Speichert alle belegten Slots dieses Teams."));
            inventory.setItem(47, item(Material.BARRIER, "§cStartinventar zurücksetzen",
                    "§7Entfernt die Team-Konfiguration."));
            inventory.setItem(49, item(Material.ARROW, "§eZurück zur Team-Auswahl"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            loadoutEditorBackups.putIfAbsent(player.getUniqueId(), new StartInventoryData(
                    player.getInventory().getContents(),
                    player.getInventory().getArmorContents(),
                    player.getInventory().getExtraContents()));
            player.openInventory(inventory);
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            player.sendMessage("§cStartinventar konnte nicht geladen werden: " + exception.getMessage());
            openTeamLoadouts(player, arenaId);
        }
    }

    private void restoreLoadoutEditorBackup(Player player) {
        StartInventoryData backup = loadoutEditorBackups.remove(player.getUniqueId());
        if (backup == null) return;
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(backup.contents());
        inventory.setArmorContents(backup.armor());
        inventory.setExtraContents(backup.extra());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)
                || holder.screen != Screen.LOADOUT_EDITOR) return;
        if (event.getPlayer() instanceof Player player) restoreLoadoutEditorBackup(player);
    }

    private void saveLoadout(Player player, MenuHolder holder, boolean reset) {
        try {
            Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
            String encoded = "";
            if (!reset) {
                ItemStack[] contents = new ItemStack[36];
                ItemStack[] armor = new ItemStack[4];
                ItemStack[] extra = new ItemStack[1];
                for (int i = 0; i < contents.length; i++) {
                    ItemStack stack = holder.inventory.getItem(i);
                    contents[i] = stack == null ? null : stack.clone();
                }
                for (int i = 0; i < armor.length; i++) {
                    ItemStack stack = holder.inventory.getItem(36 + i);
                    armor[i] = stack == null ? null : stack.clone();
                }
                ItemStack offhand = holder.inventory.getItem(40);
                extra[0] = offhand == null ? null : offhand.clone();
                encoded = new StartInventoryData(contents, armor, extra).encode();
            }
            plugin.getArenaService().save(arena.withModeSetting("loadouts", holder.secondaryId, encoded));
            player.sendMessage(reset ? "§aStartinventar zurückgesetzt." : "§aTeam-Startinventar gespeichert.");
            openTeamLoadouts(player, holder.arenaId);
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            player.sendMessage("§cStartinventar konnte nicht gespeichert werden: " + exception.getMessage());
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
            openSetup(player, arena.id());
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cVorlage konnte nicht verwendet werden: " + exception.getMessage());
        }
    }

    /** Guided, resumable arena setup. Progress is derived from the persisted arena configuration. */
    private void openSetup(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            setupArenaIds.put(player.getUniqueId(), arena.id());
            var template = plugin.getMapManager().inspectTemplate(arena.worldName());
            boolean defaultSpawn = arena.spawnPoint("default").isPresent();
            long teamSpawns = arena.spawnPoints().keySet().stream()
                    .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("team:")).count();
            boolean insideTemplate = plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName());
            MenuHolder holder = new MenuHolder(Screen.SETUP, arena.id(), null);
            Inventory inventory = inventory(holder, "ManHunt • Arena-Setup");
            inventory.setItem(4, item(Material.WRITABLE_BOOK, "§eArena einrichten: " + arena.displayName(),
                    "§7Vorlage: §f" + arena.worldName(),
                    "§7Deine Fortschritte werden automatisch gespeichert.",
                    "§7Du kannst das Setup jederzeit verlassen und fortsetzen."));
            inventory.setItem(10, item(template.valid() ? Material.LIME_DYE : Material.BARRIER,
                    "§a1. Kartenvorlage auswählen",
                    "§7" + (template.valid() ? "Ausgewählt: " + arena.worldName() : "Vorlage ist ungültig."),
                    "§7Dieser Schritt ist " + (template.valid() ? "§aerledigt" : "§coffen") + "§7."));
            inventory.setItem(12, item(insideTemplate ? Material.LIME_DYE : Material.COMPASS,
                    "§a2. Vorlagenwelt betreten",
                    "§7" + (insideTemplate ? "Du befindest dich bereits in der Vorlagenwelt." : "Die Welt wird geladen und geöffnet."),
                    "§eKlicken zum Betreten"));
            inventory.setItem(14, item(defaultSpawn ? Material.LIME_DYE : Material.RED_DYE,
                    "§a3. Standard-Spawn festlegen",
                    "§7" + (defaultSpawn ? "§aGesetzt" : "§cNoch nicht gesetzt"),
                    "§7Wird an deiner aktuellen Position gespeichert.",
                    "§eKlicken zum Setzen"));
            int enabledModes = arena.enabledModeIds().isEmpty()
                    ? plugin.getGameModeRegistry().getAll().size() : arena.enabledModeIds().size();
            inventory.setItem(16, item(enabledModes > 0 ? Material.LIME_DYE : Material.NAME_TAG,
                    "§a4. Spielmodi freigeben",
                    "§7Freigegebene Modi: §f" + enabledModes,
                    "§7Lege fest, welche Modi diese Karte nutzen dürfen.",
                    "§eKlicken zur Auswahl"));
            inventory.setItem(30, item(teamSpawns > 0 ? Material.LIME_DYE : Material.CHEST,
                    "§a5. Team-Spawnpunkte einrichten",
                    "§7Gespeicherte Team-Spawnpunkte: §f" + teamSpawns,
                    "§7Es werden nur benötigte Teams angezeigt.",
                    "§eKlicken für die Team-Einrichtung"));
            inventory.setItem(32, item(Material.CHEST,
                    "§a6. Team-Startinventare",
                    "§7Lege Items, Rüstung und Schildhand",
                    "§7für jedes Team separat fest.",
                    "§eKlicken zur Konfiguration"));
            inventory.setItem(34, item(Material.BOOK,
                    "§e7. Abschlussprüfung",
                    "§7Prüft nur die für diese Arena freigegebenen Modi.",
                    "§7Zeigt fehlende Pflicht-Spawns und Hinweise.",
                    "§eKlicken für die vollständige Prüfung"));
            inventory.setItem(45, item(Material.ARROW, "§eZur Arena-Übersicht"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eFortschritt aktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cSetup konnte nicht geladen werden: " + exception.getMessage());
            openArenaList(player);
        }
    }

    /** Shows only the team spawn points required by this arena's enabled modes. */
    private void openTeamSetup(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            setupArenaIds.put(player.getUniqueId(), arena.id());
            java.util.Set<String> teamKeys = new java.util.LinkedHashSet<>();
            for (var factory : plugin.getGameModeRegistry().getAll()) {
                if (!arena.supportsMode(factory.getId())) continue;
                for (String required : factory.createSession().getRequiredArenaSpawnPoints()) {
                    if (required.startsWith("team:")) teamKeys.add(required);
                }
            }
            Map<String, String> labels = Map.of(
                    "hitmen", "Hitmen", "survivors", "Überlebende", "juggernaut", "Juggernaut",
                    "hunters", "Jäger", "assassins", "Assassinen", "infected", "Infizierte");
            Map<String, Material> icons = Map.of(
                    "hitmen", Material.IRON_SWORD, "survivors", Material.SHIELD,
                    "juggernaut", Material.NETHERITE_CHESTPLATE, "hunters", Material.BOW,
                    "assassins", Material.DIAMOND_SWORD, "infected", Material.ROTTEN_FLESH);
            MenuHolder holder = new MenuHolder(Screen.TEAM_SETUP, arena.id(), null);
            Inventory inventory = inventory(holder, "ManHunt • Team-Spawns");
            inventory.setItem(4, item(Material.CHEST, "§e5. Team-Spawnpunkte einrichten",
                    "§7Hier erscheinen nur Teams, die von den",
                    "§7freigegebenen Spielmodi benötigt werden.",
                    "§7Klick fügt einen weiteren Spawnpunkt hinzu."));
            int slot = 10;
            int configured = 0;
            for (String key : teamKeys) {
                if (slot > 43) break;
                String teamId = key.substring("team:".length());
                String label = labels.getOrDefault(teamId, teamId.substring(0, 1).toUpperCase(Locale.ROOT) + teamId.substring(1));
                int count = spawnCount(arena, key);
                configured += count;
                holder.spawnActions.put(slot, key);
                inventory.setItem(slot++, item(count > 0 ? Material.LIME_DYE : icons.getOrDefault(teamId, Material.IRON_SWORD),
                        (count > 0 ? "§a" : "§e") + label,
                        "§7Gespeicherte Spawnpunkte: §f" + count,
                        "§eKlicken: Spawnpunkt an deiner Position hinzufügen"));
            }
            if (teamKeys.isEmpty()) {
                inventory.setItem(22, item(Material.LIME_CONCRETE, "§aKeine Team-Spawns erforderlich",
                        "§7Die freigegebenen Spielmodi verwenden",
                        "§7den Weltspawn oder andere Spawnpunkte."));
            }
            if (arena.supportsMode("hitman")) {
                int extractionCount = spawnCount(arena, "extraction");
                configured += extractionCount;
                holder.spawnActions.put(16, "extraction");
                inventory.setItem(16, item(extractionCount > 0 ? Material.LIME_DYE : Material.ENDER_EYE,
                        "§eEvakuierungspunkt (optional)",
                        "§7Status: " + (extractionCount > 0 ? "§aGesetzt" : "§7Nicht gesetzt"),
                        "§7Hitman-Überlebende können hier entkommen.",
                        "§eKlicken: Punkt an deiner Position setzen"));
            }
            inventory.setItem(31, item(Material.BOOK, "§eTeam-Spawns: " + configured,
                    "§7Nur Pflicht-Teams der aktiven Modi werden gezeigt.",
                    "§eKlicken für die Abschlussprüfung"));
            inventory.setItem(45, item(Material.ARROW, "§eZurück zur Modusauswahl"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eFortschritt aktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cTeam-Setup konnte nicht geladen werden: " + exception.getMessage());
            openModeSetup(player, arenaId);
        }
    }

    /** Lets administrators choose which registered modes may use this arena. */
    private void openModeSetup(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            setupArenaIds.put(player.getUniqueId(), arena.id());
            List<de.jawollo07.manhunt.modes.GameModeFactory> modes =
                    new java.util.ArrayList<>(plugin.getGameModeRegistry().getAll());
            MenuHolder holder = new MenuHolder(Screen.MODE_SETUP, arena.id(), null);
            Inventory inventory = inventory(holder, "ManHunt • Spielmodi freigeben");
            inventory.setItem(4, item(Material.NAME_TAG, "§e4. Unterstützte Spielmodi",
                    "§7Grün = freigegeben, grau = deaktiviert.",
                    "§7Änderungen werden sofort gespeichert.",
                    "§7Leere alte Einstellung bedeutet: alle Modi."));
            java.util.Set<String> selected = arena.enabledModeIds().isEmpty()
                    ? modes.stream().map(de.jawollo07.manhunt.modes.GameModeFactory::getId)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                    : new java.util.LinkedHashSet<>(arena.enabledModeIds());
            int slot = 10;
            for (var mode : modes) {
                if (slot > 43) break;
                boolean enabled = selected.stream().anyMatch(id -> id.equalsIgnoreCase(mode.getId()));
                holder.entries.put(slot, mode.getId());
                inventory.setItem(slot++, item(enabled ? Material.LIME_CONCRETE : Material.GRAY_DYE,
                        (enabled ? "§a" : "§7") + mode.getDisplayName(),
                        "§7ID: §f" + mode.getId(),
                        enabled ? "§aFür diese Arena freigegeben" : "§cFür diese Arena deaktiviert",
                        "§eLinksklick: Freigabe umschalten",
                        "§eRechtsklick: Modus-Einstellungen"));
            }
            inventory.setItem(31, item(Material.BOOK, "§eWeiter: Team-Spawnpunkte (" + selected.size() + " Modi)",
                    "§7Mindestens ein Spielmodus muss aktiv bleiben.",
                    "§7Nur Teams der freigegebenen Modi werden angezeigt."));
            inventory.setItem(45, item(Material.ARROW, "§eZurück zum Arena-Setup"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cSpielmodus-Auswahl konnte nicht geladen werden: " + exception.getMessage());
            openSetup(player, arenaId);
        }
    }

    private String modeSettingsSummary(Arena arena, String modeId) {
        String time = settingValueLabel("time_limit_seconds", arena.modeSetting(modeId, "time_limit_seconds", "0"));
        return switch (modeId.toLowerCase(Locale.ROOT)) {
            case "assassin" -> "Ziele " + settingValueLabel("target_distribution",
                    arena.modeSetting(modeId, "target_distribution", "cycle")) + ", Zeit " + time;
            case "hitman" -> "Hitmen " + settingValueLabel("hitman_count", arena.modeSetting(modeId, "hitman_count", "0"))
                    + ", Evakuierung " + settingValueLabel("extraction_enabled", arena.modeSetting(modeId, "extraction_enabled", "true"))
                    + ", Radius " + arena.modeSetting(modeId, "extraction_radius", "4") + " Blöcke, Zeit " + time;
            case "juggernaut" -> arena.modeSetting(modeId, "boss_health", "40") + " HP, "
                    + (int) java.util.stream.Stream.of("ability_slam", "ability_charge", "ability_roar", "ability_regeneration")
                            .filter(key -> arena.modeBooleanSetting(modeId, key, true)).count() + "/4 Fähigkeiten, Zeit " + time;
            case "infection" -> "Start-Infizierte "
                    + settingValueLabel("initial_infected", arena.modeSetting(modeId, "initial_infected", "0"))
                    + ", Zeit " + time;
            default -> "Standard";
        };
    }

    private Map<String, List<String>> modeSettingOptions(String modeId) {
        Map<String, List<String>> options = new java.util.LinkedHashMap<>();
        options.put("time_limit_seconds", List.of("0", "300", "600", "900", "1200", "1800", "2400"));
        switch (modeId.toLowerCase(Locale.ROOT)) {
            case "assassin" -> options.put("target_distribution", List.of("cycle", "random"));
            case "hitman" -> {
                options.put("hitman_count", List.of("0", "1", "2", "3"));
                options.put("extraction_enabled", List.of("true", "false"));
                options.put("extraction_radius", List.of("2", "4", "6", "8", "12"));
            }
            case "juggernaut" -> {
                options.put("boss_health", List.of("20", "30", "40", "60"));
                options.put("ability_slam", List.of("true", "false"));
                options.put("ability_charge", List.of("true", "false"));
                options.put("ability_roar", List.of("true", "false"));
                options.put("ability_regeneration", List.of("true", "false"));
            }
            case "infection" -> options.put("initial_infected", List.of("0", "1", "2", "3"));
            default -> { }
        }
        return options;
    }

    private String settingLabel(String key) {
        return switch (key) {
            case "time_limit_seconds" -> "Zeitlimit";
            case "target_distribution" -> "Zielverteilung";
            case "hitman_count" -> "Anzahl Hitmen";
            case "extraction_enabled" -> "Evakuierung";
            case "extraction_radius" -> "Evakuierungsradius";
            case "boss_health" -> "Juggernaut-Leben";
            case "ability_slam" -> "Ground Slam";
            case "ability_charge" -> "Charge";
            case "ability_roar" -> "Roar";
            case "ability_regeneration" -> "Regeneration";
            case "initial_infected" -> "Start-Infizierte";
            case "disconnect_grace_seconds" -> "Disconnect-Kulanz";
            default -> key;
        };
    }

    private String settingValueLabel(String key, String value) {
        if (key.equals("time_limit_seconds")) {
            int seconds = Integer.parseInt(value);
            return seconds == 0 ? "Server-Standard" : (seconds / 60) + " Minuten";
        }
        if (key.equals("disconnect_grace_seconds")) {
            int seconds = Integer.parseInt(value);
            return seconds == 0 ? "Deaktiviert" : seconds + " Sekunden";
        }
        if (key.equals("hitman_count") || key.equals("initial_infected"))
            return value.equals("0") ? "Automatisch" : value;
        if (key.equals("boss_health")) return value + " HP";
        if (key.equals("extraction_radius")) return value + " Blöcke";
        if (key.equals("target_distribution")) return value.equals("random") ? "Zufällig" : "Kreisförmig";
        return value.equals("true") ? "Aktiv" : "Deaktiviert";
    }

    private void openModeSettings(Player player, String arenaId, String modeId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            if (!arena.supportsMode(modeId)) throw new IllegalStateException("Modus ist nicht freigegeben.");
            String name = plugin.getGameModeRegistry().get(modeId)
                    .map(de.jawollo07.manhunt.modes.GameModeFactory::getDisplayName).orElse(modeId);
            MenuHolder holder = new MenuHolder(Screen.MODE_SETTINGS, arena.id(), modeId);
            Inventory inventory = inventory(holder, "ManHunt • " + name);
            inventory.setItem(4, item(Material.COMPARATOR, "§eEinstellungen: " + name,
                    "§7Klicke einen Eintrag an, um ihn zu ändern.",
                    "§7Änderungen werden sofort gespeichert.",
                    "§7Server-Standard nutzt die globale Rundendauer."));
            int slot = 10;
            for (Map.Entry<String, List<String>> entry : modeSettingOptions(modeId).entrySet()) {
                String key = entry.getKey();
                String fallback = switch (key) {
                    case "time_limit_seconds" -> "0";
                    case "target_distribution" -> "cycle";
                    case "hitman_count", "initial_infected" -> "0";
                    case "boss_health" -> "40";
                    case "extraction_radius" -> "4";
                    default -> "true";
                };
                holder.entries.put(slot, key);
                inventory.setItem(slot++, item(Material.REPEATER, "§e" + settingLabel(key),
                        "§7Aktuell: §f" + settingValueLabel(key, arena.modeSetting(modeId, key, fallback)),
                        "§eKlicken zum Ändern"));
            }
            inventory.setItem(45, item(Material.ARROW, "§eZurück zur Modusauswahl"));
            inventory.setItem(49, item(Material.SUNFLOWER, "§eAktualisieren"));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cEinstellungen konnten nicht geladen werden: " + exception.getMessage());
            openModeSetup(player, arenaId);
        }
    }

    private void cycleModeSetting(Player player, String arenaId, String modeId, String key) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            List<String> options = modeSettingOptions(modeId).get(key);
            if (options == null) throw new IllegalArgumentException("Unbekannte Einstellung.");
            String fallback = switch (key) {
                case "time_limit_seconds" -> "0";
                case "target_distribution" -> "cycle";
                case "hitman_count", "initial_infected" -> "0";
                case "boss_health" -> "40";
                case "extraction_radius" -> "4";
                default -> "true";
            };
            String current = arena.modeSetting(modeId, key, fallback);
            int index = options.indexOf(current);
            String next = options.get((index + 1 + options.size()) % options.size());
            plugin.getArenaService().save(arena.withModeSetting(modeId, key, next));
            player.sendMessage("§a" + settingLabel(key) + ": §f" + settingValueLabel(key, next));
            openModeSettings(player, arenaId, modeId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cEinstellung konnte nicht gespeichert werden: " + exception.getMessage());
            openModeSettings(player, arenaId, modeId);
        }
    }

    private void cycleDisconnectGrace(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            List<String> options = List.of("0", "15", "30", "60", "90", "120", "180", "300");
            String current = arena.modeSetting("general", "disconnect_grace_seconds", "60");
            int index = options.indexOf(current);
            String next = options.get((index + 1 + options.size()) % options.size());
            plugin.getArenaService().save(arena.withModeSetting("general", "disconnect_grace_seconds", next));
            player.sendMessage("§aDisconnect-Kulanz: §f" + settingValueLabel("disconnect_grace_seconds", next));
            openDetails(player, arenaId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cDisconnect-Kulanz konnte nicht gespeichert werden: " + exception.getMessage());
            openDetails(player, arenaId);
        }
    }

    private void toggleArenaMode(Player player, String arenaId, String modeId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            java.util.Set<String> selected = arena.enabledModeIds().isEmpty()
                    ? plugin.getGameModeRegistry().getAll().stream()
                            .map(de.jawollo07.manhunt.modes.GameModeFactory::getId)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                    : new java.util.LinkedHashSet<>(arena.enabledModeIds());
            String existing = selected.stream().filter(id -> id.equalsIgnoreCase(modeId)).findFirst().orElse(null);
            if (existing != null) {
                if (selected.size() == 1) {
                    player.sendMessage("§cMindestens ein Spielmodus muss für die Arena freigegeben bleiben.");
                    openModeSetup(player, arenaId);
                    return;
                }
                selected.remove(existing);
            } else {
                selected.add(modeId);
            }
            plugin.getArenaService().save(arena.withEnabledModes(selected));
            player.sendMessage("§aSpielmodus-Freigabe wurde gespeichert.");
            openModeSetup(player, arenaId);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cSpielmodus konnte nicht gespeichert werden: " + exception.getMessage());
            openModeSetup(player, arenaId);
        }
    }

    /** Shows the actual readiness of every registered mode against this arena's saved layout. */
    private void openSetupReview(Player player, String arenaId) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            var template = plugin.getMapManager().inspectTemplate(arena.worldName());
            MenuHolder holder = new MenuHolder(Screen.REVIEW, arena.id(), null);
            Inventory inventory = inventory(holder, "ManHunt • Arena prüfen");
            inventory.setItem(4, item(template.valid() && !template.loaded()
                            ? Material.EMERALD_BLOCK : Material.YELLOW_CONCRETE,
                    "§eAbschlussprüfung: " + arena.displayName(),
                    "§7Vorlage: " + (template.valid() ? "§aGültig" : "§cUngültig"),
                    "§7Vorlagenwelt: " + (template.loaded() ? "§eNoch geladen – vor Spielstart entladen"
                            : "§aNicht geladen"),
                    "§7Standard-Spawn: " + (arena.spawnPoint("default").isPresent()
                            ? "§aGesetzt" : "§eOptional – Weltspawn wird verwendet"),
                    "§7Nur freigegebene Modi werden anhand ihrer Pflicht-Spawnpunkte geprüft.",
                    "§7Freigegebene Modi: §f" + (arena.enabledModeIds().isEmpty() ? "Alle" : arena.enabledModeIds().size())));
            int[] slots = {19, 21, 23, 25};
            int index = 0;
            int checked = 0;
            boolean allReady = true;
            for (var factory : plugin.getGameModeRegistry().getAll()) {
                if (!arena.supportsMode(factory.getId())) continue;
                var mode = factory.createSession();
                ArenaValidation result = ArenaValidation.check(arena, mode, template.valid(), template.loaded());
                allReady &= result.ready();
                checked++;
                if (index >= slots.length) continue;
                List<String> lore = new java.util.ArrayList<>();
                lore.add("§7Status: " + (result.ready() ? "§aBereit" : "§cNicht bereit"));
                lore.add("§7Benötigte Team-Spawns: §f"
                        + (mode.getRequiredArenaSpawnPoints().isEmpty() ? "Keine – Weltspawn-Fallback"
                        : String.join(", ", mode.getRequiredArenaSpawnPoints())));
                lore.add("§7Arena-Einstellungen: §f" + modeSettingsSummary(arena, factory.getId()));
                result.errors().stream().limit(3).forEach(error -> lore.add("§c✗ " + error));
                result.warnings().stream().limit(2).forEach(warning -> lore.add("§e! " + warning));
                if (result.errors().isEmpty() && result.warnings().isEmpty()) lore.add("§aAlle Prüfungen bestanden.");
                inventory.setItem(slots[index++], item(result.ready() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                        (result.ready() ? "§a" : "§c") + mode.getDisplayName(), lore.toArray(String[]::new)));
            }
            inventory.setItem(31, item(allReady ? Material.EMERALD_BLOCK : Material.YELLOW_CONCRETE,
                    allReady ? "§aAlle Modi sind startbereit" : "§eEs gibt noch offene Punkte",
                    "§7" + checked + " Spielmodi geprüft." + (checked > slots.length ? " (erste vier angezeigt)" : ""),
                    "§7Eine Arena kann trotz offener Modi gespeichert bleiben.",
                    "§7Nur der ausgewählte Modus muss vor Spielstart bereit sein."));
            inventory.setItem(45, item(Material.ARROW, "§eZurück zum Team-Setup"));
            inventory.setItem(49, item(Material.BOOK, "§eArena-Details öffnen",
                    "§7Alle gespeicherten Spawnpunkte ansehen und testen."));
            inventory.setItem(53, item(Material.BARRIER, "§cSchließen"));
            player.openInventory(inventory);
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage("§cAbschlussprüfung fehlgeschlagen: " + exception.getMessage());
            openSetup(player, arenaId);
        }
    }

    private void setSpawn(Player player, String arenaId, String spawnName) {
        try {
            Arena arena = plugin.getArenaService().find(arenaId).orElseThrow(
                    () -> new IllegalStateException("Arena nicht gefunden."));
            if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
                player.sendMessage("§eDie passende Vorlagenwelt wird zum Setzen des Spawnpunkts geöffnet.");
                enterTemplateForEditing(player, arena.worldName());
                player.sendMessage("§7Gehe an die gewünschte Position und klicke anschließend im wieder geöffneten Setup-Menü erneut auf das Team.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        if (spawnName.equals("default")) openSetup(player, arenaId);
                        else openTeamSetup(player, arenaId);
                    }
                }, 2L);
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
            if (storedName.equals("default")) openModeSetup(player, arenaId);
            else openTeamSetup(player, arenaId);
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
        if (holder.screen == Screen.LOADOUT_EDITOR) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player editor)) return;
            if (event.getRawSlot() < 0) return;
            if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                if (!event.isShiftClick()) event.setCancelled(false);
                return;
            }
            if (!allowed(editor)) return;
            int editorSlot = event.getRawSlot();
            if (editorSlot <= 40) {
                event.setCancelled(false);
                return;
            }
            event.setCancelled(true);
            if (editorSlot == 45) saveLoadout(editor, holder, false);
            else if (editorSlot == 47) saveLoadout(editor, holder, true);
            else if (editorSlot == 49) openTeamLoadouts(editor, holder.arenaId);
            else if (editorSlot == 53) editor.closeInventory();
            return;
        }
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
        } else if (holder.screen == Screen.SETUP) {
            if (slot == 45) {
                setupArenaIds.remove(player.getUniqueId());
                openArenaList(player);
                return;
            }
            if (slot == 49) { openSetup(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            if (slot == 12) {
                try {
                    Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
                    enterTemplateForEditing(player, arena.worldName());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) openSetup(player, holder.arenaId);
                    }, 2L);
                } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
                    player.sendMessage("§cVorlagenwelt konnte nicht geöffnet werden: " + exception.getMessage());
                }
                return;
            }
            if (slot == 14) { setSpawn(player, holder.arenaId, "default"); return; }
            if (slot == 16) { openModeSetup(player, holder.arenaId); return; }
            if (slot == 30) { openTeamSetup(player, holder.arenaId); return; }
            if (slot == 32) { openTeamLoadouts(player, holder.arenaId); return; }
            if (slot == 34) { openSetupReview(player, holder.arenaId); return; }
        } else if (holder.screen == Screen.TEAM_SETUP) {
            if (slot == 45) { openModeSetup(player, holder.arenaId); return; }
            if (slot == 31) { openSetupReview(player, holder.arenaId); return; }
            if (slot == 49) { openTeamSetup(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String spawnName = holder.spawnActions.get(slot);
            if (spawnName != null) { setSpawn(player, holder.arenaId, spawnName); return; }
        } else if (holder.screen == Screen.MODE_SETUP) {
            if (slot == 45) { openSetup(player, holder.arenaId); return; }
            if (slot == 31) { openTeamSetup(player, holder.arenaId); return; }
            if (slot == 49) { openModeSetup(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String modeId = holder.entries.get(slot);
            if (modeId != null) {
                if (event.getClick().isRightClick()) openModeSettings(player, holder.arenaId, modeId);
                else toggleArenaMode(player, holder.arenaId, modeId);
                return;
            }
        } else if (holder.screen == Screen.MODE_SETTINGS) {
            if (slot == 45) { openModeSetup(player, holder.arenaId); return; }
            if (slot == 49) { openModeSettings(player, holder.arenaId, holder.secondaryId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String key = holder.entries.get(slot);
            if (key != null) { cycleModeSetting(player, holder.arenaId, holder.secondaryId, key); return; }
        } else if (holder.screen == Screen.REVIEW) {
            if (slot == 45) { openTeamSetup(player, holder.arenaId); return; }
            if (slot == 49) { openDetails(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
        } else if (holder.screen == Screen.DETAILS) {
            if (slot == 51) { openDetails(player, holder.arenaId, holder.page - 1); return; }
            if (slot == 52) { openDetails(player, holder.arenaId, holder.page + 1); return; }
            if (slot == 44) { openSetup(player, holder.arenaId); return; }
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
            if (slot == 26) { cycleDisconnectGrace(player, holder.arenaId); return; }
            if (slot == 27) { openTeamLoadouts(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String spawnName = holder.spawnActions.get(slot);
            if (spawnName != null) { setSpawn(player, holder.arenaId, spawnName); return; }
            spawnName = holder.spawnEntries.get(slot);
            if (spawnName != null) {
                if (event.getClick().isRightClick()) {
                    try {
                        Arena arena = plugin.getArenaService().find(holder.arenaId).orElseThrow();
                        ArenaSpawnPoint point = arena.spawnPoint(spawnName).orElseThrow(
                                () -> new IllegalArgumentException("Dieser Spawnpunkt existiert nicht mehr."));
                        String activeTemplate = editingTemplateIds.get(player.getUniqueId());
                        if (activeTemplate != null && !activeTemplate.equalsIgnoreCase(arena.worldName())) {
                            throw new IllegalStateException("Du bearbeitest bereits Vorlage " + activeTemplate
                                    + ". Verlasse diese Vorlage zuerst.");
                        }
                        if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
                            enterTemplateForEditing(player, arena.worldName());
                        }
                        World templateWorld = plugin.getMapManager().getLoadedTemplateWorld(arena.worldName());
                        if (templateWorld == null) {
                            player.sendMessage("§cDie Vorlagenwelt konnte nicht zum Testen geladen werden.");
                            return;
                        }
                        if (!player.getWorld().equals(templateWorld) && !player.teleport(templateWorld.getSpawnLocation())) {
                            player.sendMessage("§cTeleport in die Vorlagenwelt fehlgeschlagen.");
                            return;
                        }
                        if (!player.teleport(point.toLocation(templateWorld))) {
                            player.sendMessage("§cTeleport zum Spawnpunkt fehlgeschlagen.");
                            return;
                        }
                        player.sendMessage("§aDu testest jetzt Spawnpunkt §e" + spawnName
                                + " §7in Vorlage §f" + arena.worldName() + "§a.");
                    } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
                        player.sendMessage("§cSpawnpunkt konnte nicht getestet werden: " + exception.getMessage());
                    }
                } else openConfirm(player, holder.arenaId, spawnName);
            }
        } else if (holder.screen == Screen.LOADOUT_TEAMS) {
            if (slot == 45) { openDetails(player, holder.arenaId); return; }
            if (slot == 49) { openTeamLoadouts(player, holder.arenaId); return; }
            if (slot == 53) { player.closeInventory(); return; }
            String teamKey = holder.entries.get(slot);
            if (teamKey != null) { openLoadoutEditor(player, holder.arenaId, teamKey); return; }
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
                    plugin.getArenaService().save(new Arena(arena.id(), value, arena.worldName(), arena.spawnPoints(), arena.enabledModeIds(), arena.modeSettings()));
                    player.sendMessage("§aAnzeigename wurde geändert.");
                    openDetails(player, pending.arenaId());
                }
                case CHANGE_TEMPLATE -> {
                    var template = plugin.getMapManager().inspectTemplate(value);
                    if (!template.valid()) throw new IllegalArgumentException("Keine gültige Vorlage: " + template.status());
                    plugin.getArenaService().save(new Arena(arena.id(), arena.displayName(), value, java.util.Map.of(), arena.enabledModeIds(), arena.modeSettings()));
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
        setupArenaIds.remove(playerId);
        loadoutEditorBackups.remove(playerId);
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
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.screen == Screen.LOADOUT_EDITOR) {
            int topSize = event.getView().getTopInventory().getSize();
            event.setCancelled(event.getRawSlots().stream()
                    .anyMatch(slot -> slot >= 41 && slot < topSize));
            return;
        }
        event.setCancelled(true);
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

    private enum Screen { ARENAS, TEMPLATES, TEMPLATE_DETAILS, SETUP, TEAM_SETUP, MODE_SETUP, MODE_SETTINGS, REVIEW, DETAILS, LOADOUT_TEAMS, LOADOUT_EDITOR, CONFIRM }
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
