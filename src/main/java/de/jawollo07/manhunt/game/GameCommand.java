package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.arena.Arena;
import de.jawollo07.manhunt.modes.GameModeFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GameCommand implements CommandExecutor, TabCompleter {
    private final ManHuntPlugin plugin;
    private BukkitTask countdownTask;
    private BukkitTask gameTickTask;

    public GameCommand(ManHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("game")) {
            help(sender, label);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "create" -> create(sender, args);
                case "join" -> join(sender);
                case "leave" -> leave(sender);
                case "start" -> start(sender);
                case "status" -> status(sender);
                case "stop" -> stop(sender, args);
                case "reset" -> reset(sender);
                default -> help(sender, label);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Game command database operation failed: " + exception.getMessage());
            sender.sendMessage("§cDie Datenbankoperation ist fehlgeschlagen.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage("§c" + exception.getMessage());
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) throws SQLException {
        requireAdmin(sender);
        requireArgs(args, 4, "Verwendung: /manhunt game create <modus> <arena>");
        if (plugin.getGameManager().hasActiveGame()) {
            throw new IllegalStateException("Es läuft bereits eine Runde. Setze sie zuerst zurück.");
        }
        GameModeFactory factory = plugin.getGameModeRegistry().get(args[2]).orElseThrow(
                () -> new IllegalArgumentException("Unbekannter Modus: " + args[2]
                        + ". Verfügbare Modi: " + modeIds()));
        Arena arena = plugin.getArenaService().find(args[3]).orElseThrow(
                () -> new IllegalArgumentException("Arena nicht gefunden: " + args[3]));

        GameSettings settings = new GameSettings(
                plugin.getConfigManager().getInt("game.min-players", 2),
                plugin.getConfigManager().getInt("game.max-players", 8),
                plugin.getConfigManager().getInt("game.countdown-seconds", 10),
                plugin.getConfigManager().getInt("game.time-limit-seconds", 1800),
                plugin.getConfigManager().getBoolean("game.allow-late-join", false),
                plugin.getConfigManager().getBoolean("game.allow-spectators", true)
        );
        Game game = plugin.getGameManager().createGame(factory.createSession(), arena, settings);
        plugin.getGameManager().transition(GameState.LOBBY);
        Bukkit.broadcastMessage("§6[ManHunt] §e" + factory.getDisplayName()
                + " §7auf Arena §e" + arena.displayName() + " §7wurde erstellt.");
        Bukkit.broadcastMessage("§7Spieler können mit §e/manhunt game join §7beitreten.");
        sender.sendMessage("§aRunde erstellt. UUID: §7" + game.getId());
    }

    private void join(CommandSender sender) {
        Player player = requirePlayer(sender);
        Game game = requireGame();
        if (game.getState() != GameState.WAITING && game.getState() != GameState.LOBBY) {
            throw new IllegalStateException("Der Beitritt ist in diesem Spielstatus nicht möglich.");
        }
        if (game.hasPlayer(player.getUniqueId())) {
            throw new IllegalStateException("Du bist bereits Teil dieser Runde.");
        }
        plugin.getGameManager().addPlayer(player.getUniqueId());
        game.getMode().onPlayerJoin(game, game.getPlayer(player.getUniqueId()).orElseThrow());
        Bukkit.broadcastMessage("§a" + player.getName() + " §7ist der Runde beigetreten. §8("
                + game.getPlayerCount() + "/" + game.getSettings().maxPlayers() + ")");
    }

    private void leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        Game game = requireGame();
        if (game.getState() != GameState.WAITING && game.getState() != GameState.LOBBY) {
            throw new IllegalStateException("Während einer laufenden Runde kannst du nicht austreten.");
        }
        GamePlayer participant = game.getPlayer(player.getUniqueId()).orElseThrow(
                () -> new IllegalArgumentException("Du bist nicht Teil dieser Runde."));
        game.getMode().onPlayerQuit(game, participant);
        plugin.getGameManager().removePlayer(player.getUniqueId());
        Bukkit.broadcastMessage("§e" + player.getName() + " §7hat die Runde verlassen. §8("
                + game.getPlayerCount() + "/" + game.getSettings().maxPlayers() + ")");
    }

    private void start(CommandSender sender) {
        requireAdmin(sender);
        Game game = requireGame();
        if (countdownTask != null) throw new IllegalStateException("Der Countdown läuft bereits.");
        if (game.getState() != GameState.WAITING && game.getState() != GameState.LOBBY) {
            throw new IllegalStateException("Die Runde kann in diesem Status nicht gestartet werden.");
        }
        if (game.getPlayerCount() < game.getSettings().minPlayers()) {
            throw new IllegalStateException("Nicht genügend Spieler: benötigt werden "
                    + game.getSettings().minPlayers() + ", vorhanden sind " + game.getPlayerCount() + ".");
        }

        plugin.getGameManager().transition(GameState.COUNTDOWN);
        int seconds = game.getSettings().countdownSeconds();
        if (seconds <= 0) {
            launch(game);
            return;
        }
        final int[] remaining = {seconds};
        Bukkit.broadcastMessage("§6[ManHunt] §eDie Runde startet in " + seconds + " Sekunden!");
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Game current = plugin.getGameManager().getActiveGame().orElse(null);
            if (current != game || game.getState() != GameState.COUNTDOWN) {
                cancelCountdown();
                return;
            }
            if (game.getPlayerCount() < game.getSettings().minPlayers()) {
                cancelCountdown();
                plugin.getGameManager().transition(GameState.LOBBY);
                Bukkit.broadcastMessage("§c[ManHunt] Countdown abgebrochen: Nicht genügend Spieler.");
                return;
            }
            remaining[0]--;
            if (remaining[0] <= 0) {
                cancelCountdown();
                launch(game);
            } else if (remaining[0] <= 5 || remaining[0] % 5 == 0) {
                Bukkit.broadcastMessage("§6[ManHunt] §eStart in " + remaining[0] + "...");
            }
        }, 20L, 20L);
    }

    private void launch(Game game) {
        try {
            plugin.getGameLifecycleService().prepare();
            plugin.getGameLifecycleService().start();
            Bukkit.broadcastMessage("§a[ManHunt] Die Runde hat begonnen!");
            startGameTicker(game);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not start game " + game.getId() + ": " + exception.getMessage());
            Bukkit.broadcastMessage("§c[ManHunt] Start fehlgeschlagen: " + exception.getMessage());
            if (game.getState() == GameState.COUNTDOWN) {
                plugin.getGameManager().transition(GameState.LOBBY);
            } else if (game.getState() == GameState.STARTING) {
                finishAndReset(game, GameOutcome.ABORTED, "Start fehlgeschlagen");
            }
        }
    }

    private void stop(CommandSender sender, String[] args) {
        requireAdmin(sender);
        Game game = requireGame();
        cancelCountdown();
        cancelGameTicker();
        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "Von einem Administrator beendet";
        finishAndReset(game, GameOutcome.CANCELLED, reason);
        Bukkit.broadcastMessage("§c[ManHunt] Die Runde wurde beendet: §7" + reason);
    }

    private void finishAndReset(Game game, GameOutcome outcome, String reason) {
        Instant now = Instant.now();
        Instant started = game.getStartedAt().orElse(now);
        Duration duration = game.getDuration().orElse(Duration.ZERO);
        GameResult result = new GameResult(
                game.getId(), outcome, List.of(),
                game.getPlayers().stream().map(GamePlayer::getUniqueId).toList(),
                started, now, duration, reason
        );
        plugin.getGameLifecycleService().finish(result);
        plugin.getGameLifecycleService().beginReset();
        plugin.getGameLifecycleService().completeReset();
    }

    private void reset(CommandSender sender) {
        requireAdmin(sender);
        Game game = requireGame();
        cancelCountdown();
        cancelGameTicker();
        if (game.getState() == GameState.ENDING) {
            plugin.getGameLifecycleService().beginReset();
        }
        if (game.getState() != GameState.RESETTING) {
            throw new IllegalStateException("Nur eine beendete Runde kann zurückgesetzt werden.");
        }
        plugin.getGameLifecycleService().completeReset();
        sender.sendMessage("§aRunde und temporäre Arena wurden zurückgesetzt.");
    }

    private void status(CommandSender sender) {
        Game game = plugin.getGameManager().getActiveGame().orElse(null);
        if (game == null) {
            sender.sendMessage("§eAktuell ist keine Runde aktiv.");
            return;
        }
        sender.sendMessage("§6ManHunt-Runde");
        sender.sendMessage("§7Modus: §e" + game.getMode().getDisplayName());
        sender.sendMessage("§7Arena: §e" + game.getArena().displayName());
        sender.sendMessage("§7Status: §e" + game.getState());
        sender.sendMessage("§7Spieler: §e" + game.getPlayerCount() + "/" + game.getSettings().maxPlayers());
        game.getDuration().ifPresent(duration ->
                sender.sendMessage("§7Dauer: §e" + duration.toSeconds() + " Sekunden"));
    }

    private void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("manhunt.admin")) {
            throw new IllegalArgumentException("Dafür hast du keine Berechtigung.");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
        }
        return player;
    }

    private Game requireGame() {
        return plugin.getGameManager().getActiveGame().orElseThrow(
                () -> new IllegalStateException("Es gibt aktuell keine Runde."));
    }

    private List<String> modeIds() {
        return plugin.getGameModeRegistry().getAll().stream().map(GameModeFactory::getId).toList();
    }

    private void requireArgs(String[] args, int count, String usage) {
        if (args.length < count) throw new IllegalArgumentException(usage);
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6ManHunt-Spielverwaltung:");
        sender.sendMessage("§e/" + label + " game create <modus> <arena> §7(Admin)");
        sender.sendMessage("§e/" + label + " game join");
        sender.sendMessage("§e/" + label + " game leave");
        sender.sendMessage("§e/" + label + " game start §7(Admin)");
        sender.sendMessage("§e/" + label + " game status");
        sender.sendMessage("§e/" + label + " game stop [Grund] §7(Admin)");
        sender.sendMessage("§e/" + label + " game reset §7(Admin)");
        sender.sendMessage("§e/" + label + " modes");
    }

    private void startGameTicker(Game game) {
        cancelGameTicker();
        final long[] elapsed = {0L};
        gameTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getGameManager().getActiveGame().orElse(null) != game
                    || game.getState() != GameState.RUNNING) {
                cancelGameTicker();
                return;
            }
            try {
                elapsed[0]++;
                plugin.getGameLifecycleService().tick(elapsed[0]);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Game tick failed for " + game.getId() + ": " + exception.getMessage());
                cancelGameTicker();
            }
        }, 20L, 20L);
    }

    private void cancelGameTicker() {
        if (gameTickTask != null) {
            gameTickTask.cancel();
            gameTickTask = null;
        }
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        cancelGameTicker();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            List<String> actions = sender.hasPermission("manhunt.admin")
                    ? List.of("create", "join", "leave", "start", "status", "stop", "reset")
                    : List.of("join", "leave", "status");
            return filter(actions, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("create")
                && sender.hasPermission("manhunt.admin")) {
            return filter(modeIds(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("create")
                && sender.hasPermission("manhunt.admin")) {
            try {
                return filter(plugin.getArenaService().findAll().stream().map(Arena::id).toList(), args[3]);
            } catch (SQLException exception) {
                return List.of();
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
