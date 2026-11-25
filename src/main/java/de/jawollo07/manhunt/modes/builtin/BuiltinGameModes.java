package de.jawollo07.manhunt.modes.builtin;

import de.jawollo07.manhunt.ManHuntPlugin;
import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.modes.GameMode;
import de.jawollo07.manhunt.game.GameOutcome;
import de.jawollo07.manhunt.game.GamePlayer;
import de.jawollo07.manhunt.game.GameResult;
import de.jawollo07.manhunt.game.GameState;
import de.jawollo07.manhunt.modes.GameModeFactory;
import de.jawollo07.manhunt.player.PlayerStatus;
import de.jawollo07.manhunt.teams.Team;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Built-in mode factories. Each factory creates a new stateful session for every round;
 * all mutable state is therefore isolated to one Game instance.
 */
public final class BuiltinGameModes {
    private BuiltinGameModes() {}

    public static List<GameModeFactory> factories(ManHuntPlugin plugin) {
        return List.of(
                factory("assassin", "Assassin Manhunt", () -> new Assassin(plugin)),
                factory("hitman", "Hitman", () -> new Hitman(plugin)),
                factory("juggernaut", "Juggernaut", () -> new Juggernaut(plugin)),
                factory("infection", "Infection", () -> new Infection(plugin))
        );
    }

    private static GameModeFactory factory(String id, String name, java.util.function.Supplier<GameMode> supplier) {
        return new GameModeFactory() {
            @Override public String getId() { return id; }
            @Override public String getDisplayName() { return name; }
            @Override public GameMode createSession() { return supplier.get(); }
        };
    }

    private abstract static class BaseMode implements GameMode {
        protected final ManHuntPlugin plugin;
        protected Instant startedAt;
        protected boolean ended;

        BaseMode(ManHuntPlugin plugin) { this.plugin = plugin; }

        @Override public void onStart(Game game) {
            startedAt = Instant.now();
            for (GamePlayer participant : game.getPlayers()) participant.setStatus(PlayerStatus.PLAYING);
        }

        protected List<UUID> activePlayers(Game game) {
            return game.getPlayers().stream()
                    .filter(p -> p.getStatus() == PlayerStatus.PLAYING)
                    .map(GamePlayer::getUniqueId).toList();
        }

        protected void eliminate(Game game, GamePlayer participant) {
            participant.setStatus(PlayerStatus.ELIMINATED);
            Player player = Bukkit.getPlayer(participant.getUniqueId());
            if (player != null && player.isOnline()) player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }

        protected void announce(String message) {
            Bukkit.broadcastMessage("§6[ManHunt] §e" + message);
        }

        protected void finish(Game game, GameOutcome outcome, List<UUID> winners, String reason) {
            if (ended || game.getState() != GameState.RUNNING) return;
            ended = true;
            announce("Runde beendet: " + reason);
            if (winners.isEmpty()) {
                announce("Es gibt keinen Gewinner.");
            } else {
                String winnerNames = winners.stream()
                        .map(id -> {
                            String name = Bukkit.getOfflinePlayer(id).getName();
                            return name == null ? id.toString().substring(0, 8) : name;
                        })
                        .collect(java.util.stream.Collectors.joining("§7, §e"));
                announce("Gewinner: §e" + winnerNames);
            }
            for (GamePlayer participant : game.getPlayers()) {
                Player online = Bukkit.getPlayer(participant.getUniqueId());
                if (online == null || !online.isOnline()) continue;
                online.playSound(online.getLocation(),
                        winners.contains(participant.getUniqueId())
                                ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP
                                : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        0.9f, winners.contains(participant.getUniqueId()) ? 1.2f : 0.8f);
            }
            // Death-triggered victories must be finalized after Bukkit has completed
            // the death/respawn sequence; restoring a snapshot inside PlayerDeathEvent
            // can otherwise attempt to heal a player before respawn.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (game.getState() != GameState.RUNNING) return;
                Instant now = Instant.now();
                Instant start = game.getStartedAt().orElse(startedAt == null ? now : startedAt);
                GameResult result = new GameResult(game.getId(), outcome, winners,
                        game.getPlayers().stream().map(GamePlayer::getUniqueId).toList(),
                        start, now, Duration.between(start, now), reason);
                try {
                    plugin.getGameLifecycleService().finish(result);
                    plugin.getGameLifecycleService().beginReset();
                    plugin.getGameLifecycleService().completeReset();
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not finish " + getId() + " game: " + exception.getMessage());
                }
            });
        }

        protected boolean timedOut(Game game, long elapsedSeconds) {
            int limit = game.getSettings().timeLimitSeconds();
            return limit > 0 && elapsedSeconds >= limit;
        }

        protected Team team(Game game, String name, String color) {
            return game.getTeamManager().createTeam(name, color, game.getSettings().maxPlayers(), false);
        }

        protected void assign(Game game, GamePlayer player, Team team, String role) {
            game.getTeamManager().assignPlayer(player.getUniqueId(), team.getId());
            game.getRoleManager().assign(player.getUniqueId(), role, team.getId());
        }

        protected void apply(Player player, PotionEffectType type, int duration, int amplifier) {
            if (player != null) player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
        }
    }

    /** Every player hunts one secret target; a successful elimination inherits the victim's target. */
    private static final class Assassin extends BaseMode {
        private final Map<UUID, UUID> targets = new HashMap<>();
        private Team assassins;

        Assassin(ManHuntPlugin plugin) { super(plugin); }
        @Override public String getId() { return "assassin"; }
        @Override public String getDisplayName() { return "Assassin Manhunt"; }
        @Override public List<String> getRequiredArenaSpawnPoints() { return List.of("team:assassins"); }

        @Override public void onPrepare(Game game) {
            List<GamePlayer> players = new ArrayList<>(game.getPlayers());
            if (players.size() < 2) throw new IllegalStateException("Assassin benötigt mindestens zwei Spieler.");
            Collections.shuffle(players);
            assassins = team(game, "Assassins", "DARK_RED");
            for (int i = 0; i < players.size(); i++) {
                GamePlayer current = players.get(i);
                UUID target = players.get((i + 1) % players.size()).getUniqueId();
                targets.put(current.getUniqueId(), target);
                assign(game, current, assassins, "assassin");
                Player owner = Bukkit.getPlayer(current.getUniqueId());
                Player targetPlayer = Bukkit.getPlayer(target);
                if (owner != null) owner.sendMessage("§cDein geheimes Ziel: §e"
                        + (targetPlayer == null ? target.toString().substring(0, 8) : targetPlayer.getName()));
            }
            announce("Jeder Spieler hat ein geheimes Ziel. Eliminiere dein Ziel!");
        }

        @Override public void onPlayerDeath(Game game, GamePlayer victim, UUID killerId) {
            if (victim.isEliminated()) return;
            UUID nextTarget = targets.remove(victim.getUniqueId());
            eliminate(game, victim);
            if (killerId != null && !killerId.equals(victim.getUniqueId())
                    && game.getPlayer(killerId).filter(p -> p.getStatus() == PlayerStatus.PLAYING).isPresent()) {
                if (nextTarget == null) {
                    targets.remove(killerId);
                } else {
                    targets.put(killerId, nextTarget);
                }
                game.getRoleManager().assign(killerId, "assassin");
                Player killer = Bukkit.getPlayer(killerId);
                if (killer != null) {
                    String nextName = nextTarget == null ? "Kein weiteres Ziel" :
                            java.util.Optional.ofNullable(Bukkit.getPlayer(nextTarget))
                                    .map(Player::getName)
                                    .orElseGet(() -> {
                                        String offlineName = Bukkit.getOfflinePlayer(nextTarget).getName();
                                        return offlineName == null ? nextTarget.toString().substring(0, 8) : offlineName;
                                    });
                    killer.sendMessage("§aNeues Ziel: §e" + nextName);
                }
            }
            List<UUID> alive = activePlayers(game);
            if (alive.size() <= 1) finish(game, GameOutcome.VICTORY, alive, "Letzter Assassine übrig");
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            for (Map.Entry<UUID, UUID> assignment : targets.entrySet()) {
                Player hunter = Bukkit.getPlayer(assignment.getKey());
                if (hunter == null || !hunter.isOnline() || hunter.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                Player target = Bukkit.getPlayer(assignment.getValue());
                String targetName = target != null ? target.getName() :
                        java.util.Optional.ofNullable(Bukkit.getOfflinePlayer(assignment.getValue()).getName())
                                .orElse(assignment.getValue().toString().substring(0, 8));
                hunter.sendActionBar(LegacyComponentSerializer.legacySection()
                        .deserialize("§cDein Ziel: §e" + targetName));
            }
            if (timedOut(game, elapsedSeconds)) finish(game, GameOutcome.TIMEOUT, activePlayers(game), "Zeitlimit erreicht");
        }
    }

    /** Hitmen eliminate the survivor team; survivors win by holding out until the timer expires. */
    private static final class Hitman extends BaseMode {
        private Team hitmen;
        private Team survivors;
        Hitman(ManHuntPlugin plugin) { super(plugin); }
        @Override public String getId() { return "hitman"; }
        @Override public String getDisplayName() { return "Hitman"; }
        @Override public int getMinimumPlayers() { return 3; }
        @Override public List<String> getRequiredArenaSpawnPoints() { return List.of("team:hitmen", "team:survivors"); }

        @Override public void onPrepare(Game game) {
            List<GamePlayer> players = new ArrayList<>(game.getPlayers());
            if (players.size() < 3) throw new IllegalStateException("Hitman benötigt mindestens drei Spieler.");
            if (game.getSettings().randomTeamDistribution()) Collections.shuffle(players);
            int hitmanCount = players.size() >= 7 ? 2 : 1;
            hitmen = team(game, "Hitmen", "DARK_RED");
            survivors = team(game, "Survivors", "GREEN");
            for (int i = 0; i < players.size(); i++) {
                boolean hunter = i < hitmanCount;
                assign(game, players.get(i), hunter ? hitmen : survivors, hunter ? "hitman" : "survivor");
                Player assigned = Bukkit.getPlayer(players.get(i).getUniqueId());
                if (assigned != null) assigned.sendMessage(hunter
                        ? "§cDu bist ein Hitman. Eliminiere alle Überlebenden!"
                        : "§aDu bist Überlebender. Verstecke dich und überstehe das Zeitlimit!");
            }
            announce(hitmanCount + " Hitman(s) gegen " + (players.size() - hitmanCount) + " Überlebende!");
        }

        @Override public void onStart(Game game) {
            super.onStart(game);
            for (GamePlayer participant : game.getPlayers()) {
                Player player = Bukkit.getPlayer(participant.getUniqueId());
                if (player == null) continue;
                if ("hitman".equals(game.getRoleManager().getRoleId(participant.getUniqueId()).orElse(""))) {
                    apply(player, PotionEffectType.SPEED, 20 * 20, 0);
                } else {
                    apply(player, PotionEffectType.RESISTANCE, 20 * 10, 0);
                }
            }
        }

        @Override public void onPlayerDeath(Game game, GamePlayer victim, UUID killerId) {
            if (victim.isEliminated()) return;
            eliminate(game, victim);
            if (survivors.getMembers().contains(victim.getUniqueId())) {
                if (activeTeamCount(game, survivors) == 0) finish(game, GameOutcome.VICTORY, hitmen.getMembers().stream().toList(), "Alle Überlebenden eliminiert");
            } else if (activeTeamCount(game, hitmen) == 0) {
                finish(game, GameOutcome.VICTORY, survivors.getMembers().stream().filter(id -> game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).toList(), "Alle Hitmen eliminiert");
            }
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            if (game.getArena().spawnPoint("extraction").isPresent() && game.getMapInstance().isPresent()) {
                org.bukkit.World world = plugin.getMapManager().getWorld(game.getMapInstance().orElseThrow());
                org.bukkit.Location extraction = game.getArena().spawnPoint("extraction").orElseThrow().toLocation(world);
                List<UUID> escaped = survivors.getMembers().stream()
                        .filter(id -> game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false))
                        .filter(id -> {
                            Player player = Bukkit.getPlayer(id);
                            return player != null && player.getWorld().equals(world)
                                    && player.getLocation().distanceSquared(extraction) <= 16.0;
                        }).toList();
                if (!escaped.isEmpty()) {
                    finish(game, GameOutcome.VICTORY, escaped, "Überlebende haben den Extraktionspunkt erreicht");
                    return;
                }
            }
            if (timedOut(game, elapsedSeconds)) {
                List<UUID> winners = survivors.getMembers().stream()
                        .filter(id -> game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).toList();
                finish(game, GameOutcome.TIMEOUT, winners, "Überlebende haben das Zeitlimit überstanden");
            }
        }

        private long activeTeamCount(Game game, Team team) {
            return team.getMembers().stream().filter(id ->
                    game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).count();
        }
    }

    /** One empowered boss fights the remaining hunters; abilities are passive in this first implementation. */
    private static final class Juggernaut extends BaseMode {
        private Team bossTeam;
        private Team hunters;
        private UUID bossId;
        Juggernaut(ManHuntPlugin plugin) { super(plugin); }
        @Override public String getId() { return "juggernaut"; }
        @Override public String getDisplayName() { return "Juggernaut"; }
        @Override public List<String> getRequiredArenaSpawnPoints() { return List.of("team:juggernaut", "team:hunters"); }

        @Override public void onPrepare(Game game) {
            List<GamePlayer> players = new ArrayList<>(game.getPlayers());
            if (players.size() < 2) throw new IllegalStateException("Juggernaut benötigt mindestens zwei Spieler.");
            if (game.getSettings().randomTeamDistribution()) Collections.shuffle(players);
            bossId = players.get(0).getUniqueId();
            bossTeam = team(game, "Juggernaut", "DARK_RED");
            hunters = team(game, "Hunters", "BLUE");
            assign(game, players.get(0), bossTeam, "juggernaut");
            for (int i = 1; i < players.size(); i++) assign(game, players.get(i), hunters, "hunter");
            announce("Ein Juggernaut gegen " + (players.size() - 1) + " Jäger!");
        }

        @Override public void onStart(Game game) {
            super.onStart(game);
            Player boss = Bukkit.getPlayer(bossId);
            if (boss != null) {
                boss.setMaxHealth(40.0);
                boss.setHealth(40.0);
                apply(boss, PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1);
                apply(boss, PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1);
                boss.getInventory().addItem(
                        JuggernautAbilityListener.abilityItem(org.bukkit.Material.BLAZE_ROD, "Ground Slam"),
                        JuggernautAbilityListener.abilityItem(org.bukkit.Material.IRON_INGOT, "Charge"),
                        JuggernautAbilityListener.abilityItem(org.bukkit.Material.GOAT_HORN, "Roar"),
                        JuggernautAbilityListener.abilityItem(org.bukkit.Material.GOLDEN_APPLE, "Regeneration")
                );
                boss.sendMessage("§eFähigkeiten: §cBlaze Rod §7= Slam, §cIron Ingot §7= Charge, §cGoat Horn §7= Roar, §cGolden Apple §7= Regeneration");
            }
        }

        @Override public void onPlayerDeath(Game game, GamePlayer victim, UUID killerId) {
            if (victim.isEliminated()) return;
            eliminate(game, victim);
            if (victim.getUniqueId().equals(bossId)) {
                finish(game, GameOutcome.VICTORY, hunters.getMembers().stream().filter(id ->
                        game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).toList(), "Juggernaut besiegt");
            } else if (hunters.getMembers().stream().noneMatch(id ->
                    game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false))) {
                finish(game, GameOutcome.VICTORY, List.of(bossId), "Alle Jäger eliminiert");
            }
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            if (timedOut(game, elapsedSeconds)) finish(game, GameOutcome.TIMEOUT, List.of(bossId), "Juggernaut überlebte das Zeitlimit");
        }

        @Override public void onEnd(Game game) {
            Player boss = bossId == null ? null : Bukkit.getPlayer(bossId);
            if (boss != null) boss.setMaxHealth(20.0);
        }
    }

    /** Eliminated survivors are converted into infected players; infection wins when none remain. */
    private static final class Infection extends BaseMode {
        private Team infected;
        private Team survivors;
        Infection(ManHuntPlugin plugin) { super(plugin); }
        @Override public String getId() { return "infection"; }
        @Override public String getDisplayName() { return "Infection"; }
        @Override public List<String> getRequiredArenaSpawnPoints() { return List.of("team:infected", "team:survivors"); }

        @Override public void onPrepare(Game game) {
            List<GamePlayer> players = new ArrayList<>(game.getPlayers());
            if (players.size() < 2) throw new IllegalStateException("Infection benötigt mindestens zwei Spieler.");
            if (game.getSettings().randomTeamDistribution()) Collections.shuffle(players);
            int initial = players.size() >= 7 ? 2 : 1;
            infected = team(game, "Infected", "DARK_GREEN");
            survivors = team(game, "Survivors", "AQUA");
            for (int i = 0; i < players.size(); i++) {
                boolean isInfected = i < initial;
                assign(game, players.get(i), isInfected ? infected : survivors, isInfected ? "infected" : "survivor");
                Player assigned = Bukkit.getPlayer(players.get(i).getUniqueId());
                if (assigned != null) assigned.sendMessage(isInfected
                        ? "§2Du bist infiziert! Infiziere alle Überlebenden."
                        : "§bDu bist Überlebender. Überlebe bis zum Zeitlimit!");
            }
            announce(initial + " Infizierte wurden ausgewählt. Überlebt bis zum Zeitlimit!");
        }

        @Override public void onPlayerDeath(Game game, GamePlayer victim, UUID killerId) {
            if (victim.isEliminated()) return;
            boolean wasInfected = infected.getMembers().contains(victim.getUniqueId());
            if (wasInfected) {
                eliminate(game, victim);
            } else {
                game.getTeamManager().assignPlayer(victim.getUniqueId(), infected.getId());
                game.getRoleManager().assign(victim.getUniqueId(), "infected", infected.getId());
                victim.setStatus(PlayerStatus.PLAYING);
                Player player = Bukkit.getPlayer(victim.getUniqueId());
                if (player != null) {
                    player.sendMessage("§2Du bist infiziert! Jage die Überlebenden.");
                    apply(player, PotionEffectType.SPEED, 20 * 20, 0);
                }
            }
            long remaining = survivors.getMembers().stream().filter(id ->
                    game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).count();
            if (remaining == 0) finish(game, GameOutcome.VICTORY, infected.getMembers().stream().toList(), "Alle Spieler infiziert");
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            if (timedOut(game, elapsedSeconds)) {
                List<UUID> winners = survivors.getMembers().stream().filter(id ->
                        game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false)).toList();
                finish(game, GameOutcome.TIMEOUT, winners, "Überlebende haben das Zeitlimit erreicht");
            }
        }
    }
}
