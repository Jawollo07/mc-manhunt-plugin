package de.jawollo07.manhunt.arena;

import de.jawollo07.manhunt.ManHuntPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ArenaCommand implements CommandExecutor, TabCompleter {
    private final ManHuntPlugin plugin;

    public ArenaCommand(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cDafür hast du keine Berechtigung.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("arena")) {
            sendHelp(sender, label);
            return true;
        }

        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "gui" -> openGui(sender);
                case "edit" -> editTemplate(sender, args);
                case "leave" -> leaveTemplate(sender);
                case "templates" -> templates(sender);
                case "validate" -> validate(sender, args);
                case "rename" -> rename(sender, args);
                case "settemplate" -> setTemplate(sender, args);
                case "list" -> list(sender);
                case "create" -> create(sender, args);
                case "duplicate" -> duplicate(sender, args);
                case "delete" -> delete(sender, args);
                case "info" -> info(sender, args);
                case "setspawn" -> setSpawn(sender, args);
                case "delspawn" -> deleteSpawn(sender, args);
                case "spawn" -> spawn(sender, args);
                default -> sendHelp(sender, label);
            }
        } catch (SQLException | java.io.IOException exception) {
            plugin.getLogger().severe("Arena operation failed: " + exception.getMessage());
            sender.sendMessage("§cDie Arena-Verwaltung ist fehlgeschlagen: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage("§c" + exception.getMessage());
        }
        return true;
    }

    private void list(CommandSender sender) throws SQLException {
        List<Arena> arenas = plugin.getArenaService().findAll();
        if (arenas.isEmpty()) {
            sender.sendMessage("§eEs sind noch keine Arenen registriert.");
            return;
        }
        sender.sendMessage("§6Registrierte Arenen:");
        for (Arena arena : arenas) {
            sender.sendMessage("§e" + arena.id() + " §7- " + arena.displayName()
                    + " §8(template: " + arena.worldName() + ")");
        }
    }

    private void create(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 4) {
            sender.sendMessage("§cVerwendung: /manhunt arena create <id> <template> [Anzeigename]");
            return;
        }
        var template = plugin.getMapManager().inspectTemplate(args[3]);
        if (!template.valid()) throw new IllegalArgumentException("Ungültige Kartenvorlage: " + template.status());
        String displayName = args.length > 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))
                : args[2].replace('_', ' ').replace('-', ' ');
        Arena arena = new Arena(args[2], displayName, args[3]);
        plugin.getArenaService().save(arena);
        sender.sendMessage("§aArena §e" + arena.id() + " §awurde gespeichert.");
        if (template.loaded()) sender.sendMessage("§eHinweis: Die Vorlagenwelt ist geladen und muss vor dem Spielstart entladen werden.");
    }

    private void duplicate(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 4, "Verwendung: /manhunt arena duplicate <quelle> <neue-id> [Anzeigename]");
        Arena source = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        String displayName = args.length > 4
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))
                : source.displayName() + " (Kopie)";
        Arena copy = plugin.getArenaService().duplicate(source.id(), args[3], displayName);
        sender.sendMessage("§aArena §e" + source.id() + " §awurde als §e" + copy.id()
                + " §adupliziert. §7(" + copy.spawnPoints().size() + " Spawnpunkte übernommen)");
    }

    private void delete(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 3, "Verwendung: /manhunt arena delete <id>");
        var active = plugin.getGameManager().getActiveGame().orElse(null);
        if (active != null && active.getArena().id().equalsIgnoreCase(args[2])) {
            throw new IllegalStateException("Diese Arena wird gerade in einer Runde verwendet.");
        }
        if (plugin.getArenaService().delete(args[2])) {
            sender.sendMessage("§aArena §e" + args[2] + " §awurde gelöscht.");
        } else {
            sender.sendMessage("§cDiese Arena existiert nicht.");
        }
    }

    private void info(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 3, "Verwendung: /manhunt arena info <id>");
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        sender.sendMessage("§6Arena: §e" + arena.displayName() + " §7(" + arena.id() + ")");
        var template = plugin.getMapManager().inspectTemplate(arena.worldName());
        sender.sendMessage("§7Kartenvorlage: §f" + arena.worldName() + " " + (template.valid() ? (template.loaded() ? "§e[geladen]" : "§a[bereit]") : "§c[ungültig]"));
        sender.sendMessage("§7Vorlagenprüfung: §f" + template.status());
        if (arena.spawnPoints().isEmpty()) {
            sender.sendMessage("§eKeine Spawnpunkte konfiguriert; Weltspawn wird verwendet.");
        } else {
            sender.sendMessage("§6Spawnpunkte:");
            arena.spawnPoints().keySet().forEach(key -> sender.sendMessage("§e- " + key));
        }
    }

    private void setSpawn(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 4, "Verwendung: /manhunt arena setspawn <id> <default|team:name|extraction>");
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur von einem Spieler ausgeführt werden.");
            return;
        }
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
            throw new IllegalArgumentException("Du musst dich zum Setzen des Spawnpunkts in der Vorlagenwelt " + arena.worldName() + " befinden.");
        }
        Location location = player.getLocation();
        ArenaSpawnPoint point = new ArenaSpawnPoint(args[3],
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        plugin.getArenaService().save(arena.withSpawnPoint(point));
        sender.sendMessage("§aSpawnpunkt §e" + Arena.normalizeSpawnKey(args[3])
                + " §afür Arena §e" + arena.id() + " §agespeichert.");
    }

    private void deleteSpawn(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 4, "Verwendung: /manhunt arena delspawn <id> <spawnname>");
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        Arena updated = arena.withoutSpawnPoint(args[3]);
        if (updated.spawnPoints().size() == arena.spawnPoints().size()) {
            sender.sendMessage("§cDieser Spawnpunkt existiert nicht.");
            return;
        }
        plugin.getArenaService().save(updated);
        sender.sendMessage("§aSpawnpunkt wurde entfernt.");
    }


    private void spawn(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /manhunt arena spawn <set|list|tp|remove|clear> <Arena> [Team] [Nummer]");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            requireArgs(args, 4, "Verwendung: /manhunt arena spawn list <Arena>");
            Arena arena = requireArena(args[3]);
            sender.sendMessage("§6Team-Spawnpunkte für §e" + arena.displayName() + "§6:");
            boolean found = false;
            for (String team : List.of("assassins", "hitmen", "survivors", "juggernaut", "hunters", "infected")) {
                List<ArenaSpawnPoint> points = teamSpawnPoints(arena, team);
                if (points.isEmpty()) continue;
                found = true;
                sender.sendMessage("§e" + team + "§7: " + points.stream()
                        .map(point -> Integer.toString(spawnIndex(point.name())))
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            if (!found) sender.sendMessage("§7Noch keine Team-Spawnpunkte gesetzt.");
            return;
        }
        requireArgs(args, 5, "Verwendung: /manhunt arena spawn <set|tp|remove|clear> <Arena> <Team> [Nummer]");
        Arena arena = requireArena(args[3]);
        String team = normalizeTeam(args[4]);
        if (action.equals("set")) {
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("Dieser Befehl kann nur ingame ausgeführt werden.");
            if (!plugin.getMapManager().isTemplateWorld(player.getWorld(), arena.worldName())) {
                throw new IllegalArgumentException("Du musst dich in der Vorlagenwelt " + arena.worldName() + " befinden. Nutze /manhunt arena edit " + arena.worldName() + ".");
            }
            int next = teamSpawnPoints(arena, team).stream().mapToInt(point -> spawnIndex(point.name())).max().orElse(0) + 1;
            Location location = player.getLocation();
            ArenaSpawnPoint point = new ArenaSpawnPoint("team:" + team + ":" + next,
                    location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            plugin.getArenaService().save(arena.withSpawnPoint(point));
            sender.sendMessage("§aTeam-Spawn §e" + team + " #" + next + " §agespeichert.");
            return;
        }
        if (action.equals("clear")) {
            Arena updated = arena;
            for (ArenaSpawnPoint point : teamSpawnPoints(arena, team)) updated = updated.withoutSpawnPoint(point.name());
            plugin.getArenaService().save(updated);
            sender.sendMessage("§aAlle Spawnpunkte von §e" + team + " §awurden entfernt.");
            return;
        }
        requireArgs(args, 6, "Verwendung: /manhunt arena spawn <tp|remove> <Arena> <Team> <Nummer>");
        int index;
        try { index = Integer.parseInt(args[5]); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Die Nummer muss eine ganze Zahl sein."); }
        if (index < 1) throw new IllegalArgumentException("Die Nummer muss mindestens 1 sein.");
        List<ArenaSpawnPoint> points = teamSpawnPoints(arena, team);
        ArenaSpawnPoint selected = points.stream().filter(point -> spawnIndex(point.name()) == index)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Dieser Team-Spawnpunkt existiert nicht."));
        if (action.equals("tp")) {
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("Dieser Befehl kann nur ingame ausgeführt werden.");
            org.bukkit.World world = plugin.getMapManager().getLoadedTemplateWorld(arena.worldName());
            if (world == null) throw new IllegalStateException("Die Vorlagenwelt ist nicht geladen. Nutze /manhunt arena edit " + arena.worldName() + ".");
            player.teleport(selected.toLocation(world));
            sender.sendMessage("§aZum Spawnpunkt §e" + team + " #" + index + " §ateleportiert.");
        } else if (action.equals("remove")) {
            Arena updated = arena.withoutSpawnPoint(selected.name());
            List<ArenaSpawnPoint> remaining = teamSpawnPoints(updated, team);
            for (ArenaSpawnPoint point : remaining) updated = updated.withoutSpawnPoint(point.name());
            for (int i = 0; i < remaining.size(); i++) {
                ArenaSpawnPoint point = remaining.get(i);
                updated = updated.withSpawnPoint(new ArenaSpawnPoint("team:" + team + ":" + (i + 1),
                        point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
            }
            plugin.getArenaService().save(updated);
            sender.sendMessage("§aSpawnpunkt entfernt; Nummerierung wurde aktualisiert.");
        } else {
            sender.sendMessage("§cUnbekannte Aktion. Nutze set, list, tp, remove oder clear.");
        }
    }

    private Arena requireArena(String id) throws SQLException {
        return plugin.getArenaService().find(id).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + id));
    }

    private String normalizeTeam(String value) {
        String team = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (!List.of("assassins", "hitmen", "survivors", "juggernaut", "hunters", "infected").contains(team)) {
            throw new IllegalArgumentException("Unbekanntes Team. Erlaubt: assassins, hitmen, survivors, juggernaut, hunters, infected.");
        }
        return team;
    }

    private List<ArenaSpawnPoint> teamSpawnPoints(Arena arena, String team) {
        return arena.spawnPoints().values().stream()
                .filter(point -> point.name().equalsIgnoreCase("team:" + team)
                        || point.name().toLowerCase(Locale.ROOT).startsWith("team:" + team + ":"))
                .sorted(java.util.Comparator.comparingInt(point -> spawnIndex(point.name())))
                .toList();
    }

    private int spawnIndex(String name) {
        int separator = name.lastIndexOf(':');
        if (separator < 0) return 1;
        try { return Integer.parseInt(name.substring(separator + 1)); }
        catch (NumberFormatException exception) { return 1; }
    }

    private void openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieses Menü kann nur ingame geöffnet werden.");
            return;
        }
        plugin.getArenaManagementMenu().open(player);
    }

    private void editTemplate(CommandSender sender, String[] args) throws java.io.IOException {
        requireArgs(args, 3, "Verwendung: /manhunt arena edit <Vorlagen-ID>");
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur ingame ausgeführt werden.");
            return;
        }
        plugin.getArenaManagementMenu().enterTemplateForEditing(player, args[2]);
    }

    private void leaveTemplate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur ingame ausgeführt werden.");
            return;
        }
        plugin.getArenaManagementMenu().leaveTemplateEditing(player);
    }

    private void templates(CommandSender sender) throws java.io.IOException {
        var templates = plugin.getMapManager().listTemplates();
        if (templates.isEmpty()) {
            sender.sendMessage("§eKeine Weltvorlagen gefunden.");
            return;
        }
        sender.sendMessage("§6Gefundene Kartenvorlagen:");
        for (var template : templates) {
            sender.sendMessage((template.valid() ? "§a" : "§c") + template.id()
                    + " §7- " + template.status());
        }
    }

    private void validate(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 3, "Verwendung: /manhunt arena validate <id>");
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        var template = plugin.getMapManager().inspectTemplate(arena.worldName());
        sender.sendMessage("§6Prüfung: §e" + arena.displayName());
        sender.sendMessage((template.valid() ? "§a✔ " : "§c✘ ") + "Kartenvorlage: " + template.status());
        sender.sendMessage((arena.spawnPoint("default").isPresent() ? "§a✔ " : "§e! ")
                + "Standard-Spawnpunkt " + (arena.spawnPoint("default").isPresent() ? "konfiguriert" : "fehlt (Weltspawn wird verwendet)"));
        if (arena.spawnPoint("extraction").isPresent()) sender.sendMessage("§a✔ Extraktionspunkt konfiguriert");
        sender.sendMessage("§7Weitere Spawnpunkte: §f" + arena.spawnPoints().size());
        if (!template.valid()) sender.sendMessage("§cDiese Arena kann nicht gestartet werden, bis die Vorlage repariert ist.");
        else if (template.loaded()) sender.sendMessage("§eVorlagenwelt vor dem Start entladen.");
        else sender.sendMessage("§aVorlage ist bereit für eine Spielrunde.");
    }

    private void rename(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 4, "Verwendung: /manhunt arena rename <id> <neuer Anzeigename>");
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        plugin.getArenaService().save(new Arena(arena.id(), name, arena.worldName(), arena.spawnPoints()));
        sender.sendMessage("§aAnzeigename geändert zu §e" + name);
    }

    private void setTemplate(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 4, "Verwendung: /manhunt arena settemplate <id> <template>");
        Arena arena = plugin.getArenaService().find(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[2]));
        var template = plugin.getMapManager().inspectTemplate(args[3]);
        if (!template.valid()) throw new IllegalArgumentException("Ungültige Kartenvorlage: " + template.status());
        plugin.getArenaService().save(new Arena(arena.id(), arena.displayName(), args[3]));
        sender.sendMessage("§aKartenvorlage auf §e" + args[3] + " §agesetzt. Alte Spawnpunkte wurden zurückgesetzt.");
        if (template.loaded()) sender.sendMessage("§eHinweis: Welt muss vor Spielstart entladen werden.");
    }

    private void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException(usage);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6ManHunt Arena-Verwaltung:");
        sender.sendMessage("§e/" + label + " arena gui §7(komfortable GUI)");
        sender.sendMessage("§e/" + label + " arena edit <Vorlagen-ID> §7(Welt laden und betreten)");
        sender.sendMessage("§e/" + label + " arena leave §7(zurückkehren und entladen)");
        sender.sendMessage("§e/" + label + " arena templates");
        sender.sendMessage("§e/" + label + " arena validate <id>");
        sender.sendMessage("§e/" + label + " arena rename <id> <Anzeigename>");
        sender.sendMessage("§e/" + label + " arena settemplate <id> <template>");
        sender.sendMessage("§e/" + label + " arena list");
        sender.sendMessage("§e/" + label + " arena create <id> <template> <Anzeigename>");
        sender.sendMessage("§e/" + label + " arena duplicate <quelle> <neue-id> [Anzeigename]");
        sender.sendMessage("§e/" + label + " arena delete <id>");
        sender.sendMessage("§e/" + label + " arena info <id>");
        sender.sendMessage("§e/" + label + " arena setspawn <id> <default|team:name>");
        sender.sendMessage("§e/" + label + " arena spawn set <arena> <team> §7(Nummer automatisch)");
        sender.sendMessage("§e/" + label + " arena spawn list <arena>");
        sender.sendMessage("§e/" + label + " arena spawn tp|remove <arena> <team> <nummer>");
        sender.sendMessage("§e/" + label + " arena spawn clear <arena> <team>");
        sender.sendMessage("§e/" + label + " arena delspawn <id> <spawnname>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("manhunt.admin")) return List.of();
        if (args.length == 1) return filter(List.of("arena"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(List.of("gui", "edit", "leave", "templates", "list", "create", "duplicate", "delete", "info", "validate", "rename", "settemplate", "setspawn", "delspawn", "spawn"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("spawn")) {
            return filter(List.of("set", "list", "tp", "remove", "clear"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("spawn")) {
            try { return filter(plugin.getArenaService().findAll().stream().map(Arena::id).toList(), args[3]); }
            catch (SQLException exception) { return List.of(); }
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("spawn")
                && !args[2].equalsIgnoreCase("list")) {
            return filter(List.of("assassins", "hitmen", "survivors", "juggernaut", "hunters", "infected"), args[4]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("edit")) {
            try {
                return filter(plugin.getMapManager().listTemplates().stream().map(template -> template.id()).toList(), args[2]);
            } catch (java.io.IOException exception) {
                return List.of();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && List.of("delete", "info", "validate", "rename", "settemplate", "setspawn", "delspawn", "duplicate").contains(args[1].toLowerCase(Locale.ROOT))) {
            try {
                return filter(plugin.getArenaService().findAll().stream().map(Arena::id).toList(), args[2]);
            } catch (SQLException exception) {
                return List.of();
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("setspawn")) {
            return filter(List.of("default", "team:hitmen", "team:survivors", "team:juggernaut", "team:hunters", "team:assassins", "team:infected", "extraction"), args[3]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
