package de.jawollo07.manhunt;

import de.jawollo07.manhunt.arena.ArenaCommand;
import de.jawollo07.manhunt.game.GameCommand;
import de.jawollo07.manhunt.modes.GameModeFactory;
import de.jawollo07.manhunt.stats.StatisticsCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class ManHuntCommand implements CommandExecutor, TabCompleter {
    private final ArenaCommand arenaCommand;
    private final GameCommand gameCommand;
    private final ManHuntPlugin plugin;
    private final StatisticsCommand statisticsCommand;

    public ManHuntCommand(ManHuntPlugin plugin) {
        this.plugin = plugin;
        this.arenaCommand = new ArenaCommand(plugin);
        this.gameCommand = new GameCommand(plugin);
        this.statisticsCommand = new StatisticsCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "arena" -> arenaCommand.onCommand(sender, command, label, args);
            case "game" -> gameCommand.onCommand(sender, command, label, args);
            case "modes" -> listModes(sender);
            case "stats", "leaderboard", "top", "history" -> statisticsCommand.onCommand(sender, command, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void listModes(CommandSender sender) {
        var modes = plugin.getGameModeRegistry().getAll();
        if (modes.isEmpty()) {
            sender.sendMessage("§eEs sind noch keine Spielmodi registriert.");
            sender.sendMessage("§7Spielmodi können über die Plugin-API als GameModeFactory registriert werden.");
            return;
        }
        sender.sendMessage("§6Verfügbare Spielmodi:");
        for (GameModeFactory mode : modes) {
            sender.sendMessage("§e" + mode.getId() + " §7- " + mode.getDisplayName());
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6ManHunt-Befehle:");
        sender.sendMessage("§e/" + label + " modes");
        sender.sendMessage("§e/" + label + " arena");
        sender.sendMessage("§e/" + label + " game");
        sender.sendMessage("§e/" + label + " stats [modus]");
        sender.sendMessage("§e/" + label + " leaderboard [modus]");
        sender.sendMessage("§e/" + label + " history");
    }

    public void shutdown() {
        gameCommand.cancelCountdown();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("arena", "game", "modes", "stats", "leaderboard", "history"), args[0]);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "arena" -> arenaCommand.onTabComplete(sender, command, alias, args);
            case "game" -> gameCommand.onTabComplete(sender, command, alias, args);
            case "stats", "leaderboard", "history" -> statisticsCommand.onTabComplete(sender, command, alias, args);
            default -> List.of();
        };
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
