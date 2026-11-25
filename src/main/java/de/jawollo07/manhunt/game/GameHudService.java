package de.jawollo07.manhunt.game;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.player.PlayerStatus;
import de.jawollo07.manhunt.teams.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small, automatically refreshed in-game sidebar for the active round. */
public final class GameHudService {
    private final ManHuntPlugin plugin;
    private final GameManager gameManager;
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private int taskId = -1;

    public GameHudService(ManHuntPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::refresh, 20L, 20L);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        clear();
    }

    private void refresh() {
        Game game = gameManager.getActiveGame().orElse(null);
        if (game == null || game.getState() != GameState.RUNNING) {
            clear();
            return;
        }

        List<String> lines = buildLines(game);
        for (GamePlayer participant : game.getPlayers()) {
            Player player = Bukkit.getPlayer(participant.getUniqueId());
            if (player == null || !player.isOnline()) continue;
            Scoreboard board = activeBoards.computeIfAbsent(player.getUniqueId(), ignored -> createBoard(player));
            Objective objective = board.getObjective("manhunt");
            if (objective == null) continue;
            for (String entry : new ArrayList<>(board.getEntries())) board.resetScores(entry);
            int score = lines.size();
            for (String line : lines) objective.getScore(line).setScore(score--);
        }
    }

    private Scoreboard createBoard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) throw new IllegalStateException("Scoreboard manager is unavailable");
        previousBoards.put(player.getUniqueId(), player.getScoreboard());
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
                "manhunt", "dummy", Component.text("MANHUNT", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        return board;
    }

    private List<String> buildLines(Game game) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + game.getMode().getDisplayName());
        lines.add(ChatColor.DARK_GRAY + "----------------");
        for (Team team : game.getTeamManager().getTeams()) {
            long alive = team.getMembers().stream()
                    .filter(id -> game.getPlayer(id)
                            .map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false))
                    .count();
            lines.add(teamColor(team) + team.getName() + ChatColor.WHITE + ": " + ChatColor.YELLOW + alive);
        }
        long alive = game.getPlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.PLAYING).count();
        long eliminated = game.getPlayers().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ELIMINATED
                        || p.getStatus() == PlayerStatus.SPECTATING).count();
        lines.add(ChatColor.GREEN + "Aktiv: " + ChatColor.WHITE + alive);
        lines.add(ChatColor.RED + "Ausgeschieden: " + ChatColor.WHITE + eliminated);
        lines.add(ChatColor.DARK_GRAY + "----------------");
        long seconds = game.getStartedAt().map(start -> Duration.between(start, java.time.Instant.now()).getSeconds())
                .orElse(0L);
        lines.add(ChatColor.GOLD + "Zeit: " + ChatColor.WHITE
                + String.format("%02d:%02d", seconds / 60, seconds % 60));

        UUID ignored = null;
        // A compact personal role/team line keeps the sidebar useful without mode-specific UI logic.
        return lines;
    }

    private ChatColor teamColor(Team team) {
        return switch (team.getColor().toUpperCase(java.util.Locale.ROOT)) {
            case "DARK_RED", "RED" -> ChatColor.RED;
            case "GREEN", "DARK_GREEN" -> ChatColor.GREEN;
            case "BLUE", "DARK_BLUE" -> ChatColor.BLUE;
            case "AQUA" -> ChatColor.AQUA;
            case "YELLOW" -> ChatColor.YELLOW;
            case "GOLD" -> ChatColor.GOLD;
            case "DARK_PURPLE", "LIGHT_PURPLE" -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.WHITE;
        };
    }

    private void clear() {
        for (Map.Entry<UUID, Scoreboard> entry : activeBoards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            Scoreboard previous = previousBoards.get(entry.getKey());
            if (previous != null && player.getScoreboard() == entry.getValue()) {
                player.setScoreboard(previous);
            }
        }
        activeBoards.clear();
        previousBoards.clear();
    }
}
