package de.jawollo07.manhunt.stats;

import de.jawollo07.manhunt.ManHuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatisticsCommand implements CommandExecutor, TabCompleter {
    private final ManHuntPlugin plugin;

    public StatisticsCommand(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            showStats(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            showLeaderboard(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        if (args[0].equalsIgnoreCase("history")) {
            showHistory(sender);
            return true;
        }
        help(sender, label);
        return true;
    }

    private void showStats(CommandSender sender, String modeId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl benötigt einen Spieler als Absender.");
            return;
        }
        try {
            if ("all".equalsIgnoreCase(modeId)) modeId = null;
            else if (modeId != null) modeId = modeId.toLowerCase(Locale.ROOT);
            var stats = plugin.getStatisticsRepository().getPlayerStatistics(player.getUniqueId(), modeId);
            if (stats.isEmpty()) {
                sender.sendMessage("§eFür dich liegen" + (modeId == null ? "" : " im Modus §6" + modeId + "§e")
                        + " noch keine Statistiken vor.");
                return;
            }
            PlayerStatistics value = stats.orElseThrow();
            sender.sendMessage("§6Deine ManHunt-Statistiken" + (modeId == null ? " §7(Gesamt)" : " §7(" + modeId + ")"));
            sender.sendMessage("§7Spiele: §e" + value.gamesPlayed() + " §8| §7Siege: §a" + value.wins()
                    + " §8| §7Niederlagen: §c" + value.losses() + " §8| §7Unentschieden: §e" + value.draws());
            sender.sendMessage("§7Siegquote: §e" + String.format(Locale.ROOT, "%.1f", value.winRate())
                    + "% §8| §7Spielzeit: §e" + formatDuration(value.totalSeconds()));
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load player statistics: " + exception.getMessage());
            sender.sendMessage("§cStatistiken konnten nicht geladen werden.");
        }
    }

    private void showLeaderboard(CommandSender sender, String modeId) {
        try {
            if ("all".equalsIgnoreCase(modeId)) modeId = null;
            else if (modeId != null) modeId = modeId.toLowerCase(Locale.ROOT);
            List<LeaderboardEntry> entries = plugin.getStatisticsRepository().getLeaderboard(modeId, 10);
            sender.sendMessage("§6ManHunt-Bestenliste" + (modeId == null ? " §7(Gesamt)" : " §7(" + modeId + ")"));
            if (entries.isEmpty()) {
                sender.sendMessage("§7Noch keine gewerteten Spiele vorhanden.");
                return;
            }
            for (LeaderboardEntry entry : entries) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
                String name = offline.getName() == null ? entry.playerId().toString().substring(0, 8) : offline.getName();
                sender.sendMessage("§e#" + entry.rank() + " §f" + name + " §8- §a" + entry.wins()
                        + " Siege §8| §7" + entry.gamesPlayed() + " Spiele §8| §e"
                        + String.format(Locale.ROOT, "%.1f", entry.winRate()) + "%");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load leaderboard: " + exception.getMessage());
            sender.sendMessage("§cBestenliste konnte nicht geladen werden.");
        }
    }

    private void showHistory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl benötigt einen Spieler als Absender.");
            return;
        }
        try {
            List<GameHistoryEntry> entries = plugin.getStatisticsRepository().getRecentHistory(player.getUniqueId(), 5);
            sender.sendMessage("§6Deine letzten ManHunt-Spiele:");
            if (entries.isEmpty()) {
                sender.sendMessage("§7Noch keine Spielhistorie vorhanden.");
                return;
            }
            for (GameHistoryEntry entry : entries) {
                sender.sendMessage("§e" + entry.modeId() + " §8| §7" + entry.arenaId() + " §8| §f"
                        + entry.outcome() + " §8| §7" + formatDuration(entry.durationSeconds())
                        + " §8| §7" + entry.endedAt());
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load game history: " + exception.getMessage());
            sender.sendMessage("§cSpielhistorie konnte nicht geladen werden.");
        }
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m " + remaining + "s";
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6ManHunt-Statistiken:");
        sender.sendMessage("§e/" + label + " stats [modus]");
        sender.sendMessage("§e/" + label + " leaderboard [modus]");
        sender.sendMessage("§e/" + label + " history");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("stats", "leaderboard", "history"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats")
                || args[0].equalsIgnoreCase("leaderboard"))) {
            List<String> modes = new ArrayList<>();
            modes.add("all");
            plugin.getGameModeRegistry().getAll().forEach(mode -> modes.add(mode.getId()));
            return filter(modes, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
