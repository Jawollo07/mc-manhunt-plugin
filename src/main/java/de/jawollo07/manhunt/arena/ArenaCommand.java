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
                case "list" -> list(sender);
                case "create" -> create(sender, args);
                case "delete" -> delete(sender, args);
                case "info" -> info(sender, args);
                case "setspawn" -> setSpawn(sender, args);
                case "delspawn" -> deleteSpawn(sender, args);
                default -> sendHelp(sender, label);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Arena database operation failed: " + exception.getMessage());
            sender.sendMessage("§cDie Arena-Datenbankoperation ist fehlgeschlagen.");
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
        if (args.length < 5) {
            sender.sendMessage("§cVerwendung: /manhunt arena create <id> <template> <Anzeigename>");
            return;
        }
        String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        Arena arena = new Arena(args[2], displayName, args[3]);
        plugin.getArenaService().save(arena);
        sender.sendMessage("§aArena §e" + arena.id() + " §awurde gespeichert.");
    }

    private void delete(CommandSender sender, String[] args) throws SQLException {
        requireArgs(args, 3, "Verwendung: /manhunt arena delete <id>");
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
        sender.sendMessage("§7Kartenvorlage: §f" + arena.worldName());
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

    private void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException(usage);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6ManHunt Arena-Verwaltung:");
        sender.sendMessage("§e/" + label + " arena list");
        sender.sendMessage("§e/" + label + " arena create <id> <template> <Anzeigename>");
        sender.sendMessage("§e/" + label + " arena delete <id>");
        sender.sendMessage("§e/" + label + " arena info <id>");
        sender.sendMessage("§e/" + label + " arena setspawn <id> <default|team:name>");
        sender.sendMessage("§e/" + label + " arena delspawn <id> <spawnname>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("manhunt.admin")) return List.of();
        if (args.length == 1) return filter(List.of("arena"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(List.of("list", "create", "delete", "info", "setspawn", "delspawn"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && List.of("delete", "info", "setspawn", "delspawn").contains(args[1].toLowerCase(Locale.ROOT))) {
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
