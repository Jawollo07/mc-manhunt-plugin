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
        help(sender, label);
        return true;
    }

    private void showStats(CommandSender sender, String modeId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDiesen Befehl kannst du nur als Spieler verwenden.");
            return;
        }
        try {
            modeId = normalizeMode(modeId);
            var stats = plugin.getStatisticsRepository().getPlayerStatistics(player.getUniqueId(), modeId);
            if (stats.isEmpty()) {
                sender.sendMessage("§eDu hast" + (modeId == null ? "" : " im Modus §6" + modeId + "§e")
                        + " noch keine Runde gespielt.");
                return;
            }
            PlayerStatistics value = stats.orElseThrow();
            sender.sendMessage("§6Deine Minigame-Statistiken"
                    + (modeId == null ? " §7(Gesamt)" : " §7(" + modeId + ")"));
            sender.sendMessage("§7Gespielte Runden: §e" + value.gamesPlayed()
                    + " §8| §7Gewonnene Runden: §a" + value.wins());
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load player statistics: " + exception.getMessage());
            sender.sendMessage("§cDeine Statistiken konnten gerade nicht geladen werden.");
        }
    }

    private void showLeaderboard(CommandSender sender, String modeId) {
        try {
            modeId = normalizeMode(modeId);
            List<LeaderboardEntry> entries = plugin.getStatisticsRepository().getLeaderboard(modeId, 10);
            sender.sendMessage("§6Minigame-Bestenliste"
                    + (modeId == null ? " §7(Gesamt)" : " §7(" + modeId + ")"));
            if (entries.isEmpty()) {
                sender.sendMessage("§7Es wurden noch keine Runden gespielt.");
                return;
            }
            for (LeaderboardEntry entry : entries) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
                String name = offline.getName() == null
                        ? entry.playerId().toString().substring(0, 8) : offline.getName();
                sender.sendMessage("§e#" + entry.rank() + " §f" + name + " §8- §a"
                        + entry.wins() + " Siege §8| §7" + entry.gamesPlayed() + " Runden");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load leaderboard: " + exception.getMessage());
            sender.sendMessage("§cDie Bestenliste konnte gerade nicht geladen werden.");
        }
    }

    private String normalizeMode(String modeId) {
        if (modeId == null || modeId.equalsIgnoreCase("all")) return null;
        return modeId.toLowerCase(Locale.ROOT);
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6Minigame-Befehle:");
        sender.sendMessage("§e/" + label + " stats [modus]");
        sender.sendMessage("§e/" + label + " leaderboard [modus]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("stats", "leaderboard"), args[0]);
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
