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
            for (GamePlayer participant : game.getPlayers()) {
                if (participant.getStatus() != PlayerStatus.DISCONNECTED) {
                    participant.setStatus(PlayerStatus.PLAYING);
                }
            }
            announceRoundSettings(game);
        }

        @Override public void onPlayerJoin(Game game, GamePlayer participant) {
            Player player = Bukkit.getPlayer(participant.getUniqueId());
            if (player == null) return;
            if (participant.isEliminated()) {
                player.sendMessage("§7Du bist ausgeschieden. Du kannst die laufende Runde als Zuschauer verfolgen.");
                return;
            }
            String role = game.getRoleManager().getRoleId(participant.getUniqueId()).orElse("Teilnehmer");
            player.sendMessage("§aWillkommen zurück! §7Deine Rolle: §e" + role);
        }

        private void announceRoundSettings(Game game) {
            String mode = getId();
            int configuredLimit = game.getArena().modeIntSetting(mode, "time_limit_seconds", 0);
            int limit = configuredLimit > 0 ? configuredLimit : game.getSettings().timeLimitSeconds();
            String time = limit > 0 ? (limit / 60) + " Minuten" : "unbegrenzt";
            List<String> details = new ArrayList<>();
            details.add("Zeitlimit: " + time);
            int disconnectGrace = game.getArena().modeIntSetting("general", "disconnect_grace_seconds", 60);
            details.add("Disconnect-Kulanz: " + (disconnectGrace <= 0
                    ? "deaktiviert" : disconnectGrace + " Sekunden"));
            switch (mode.toLowerCase(java.util.Locale.ROOT)) {
                case "assassin" -> details.add("Zielverteilung: "
                        + (game.getArena().modeSetting(mode, "target_distribution", "cycle").equalsIgnoreCase("random")
                        ? "zufällig" : "kreisförmig"));
                case "hitman" -> {
                    int hitmen = game.getArena().modeIntSetting(mode, "hitman_count", 0);
                    details.add("Hitmen: " + (hitmen <= 0 ? "automatisch" : hitmen));
                    boolean extraction = game.getArena().modeBooleanSetting(mode, "extraction_enabled", true);
                    details.add("Evakuierung: " + (extraction ? "aktiv" : "deaktiviert"));
                    if (extraction) details.add("Radius: "
                            + game.getArena().modeIntSetting(mode, "extraction_radius", 4) + " Blöcke");
                }
                case "juggernaut" -> {
                    int hp = game.getArena().modeIntSetting(mode, "boss_health", 40);
                    long abilities = java.util.stream.Stream.of(
                                    "ability_slam", "ability_charge", "ability_roar", "ability_regeneration")
                            .filter(key -> game.getArena().modeBooleanSetting(mode, key, true)).count();
                    details.add("Juggernaut: " + hp + " HP, " + abilities + "/4 Fähigkeiten");
                }
                case "infection" -> {
                    int infected = game.getArena().modeIntSetting(mode, "initial_infected", 0);
                    details.add("Start-Infizierte: " + (infected <= 0 ? "automatisch" : infected));
                }
                default -> { }
            }
            announce("Rundenregeln – " + String.join(" | ", details));
        }

        /** Connected living players, used when assigning interactive targets. */
        protected List<UUID> activePlayers(Game game) {
            return game.getPlayers().stream()
                    .filter(p -> p.getStatus() == PlayerStatus.PLAYING)
                    .map(GamePlayer::getUniqueId).toList();
        }

        /** Living players including temporary disconnects, used for victory checks. */
        protected List<UUID> activeRoundPlayers(Game game) {
            return game.getPlayers().stream()
                    .filter(GamePlayer::isActiveForRound)
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
            String winnerNames = winners.stream()
                    .map(id -> {
                        String name = Bukkit.getOfflinePlayer(id).getName();
                        return name == null ? id.toString().substring(0, 8) : name;
                    })
                    .collect(java.util.stream.Collectors.joining("§7, §e"));
            if (winners.isEmpty()) {
                announce("Es gibt keinen Gewinner.");
            } else {
                announce("Gewinner: §e" + winnerNames);
            }
            for (GamePlayer participant : game.getPlayers()) {
                Player online = Bukkit.getPlayer(participant.getUniqueId());
                if (online == null || !online.isOnline()) continue;
                boolean winner = winners.contains(participant.getUniqueId());
                String title = switch (outcome) {
                    case DRAW -> "§eUnentschieden";
                    case CANCELLED, ABORTED -> "§cRunde beendet";
                    case VICTORY, TIMEOUT -> winner ? "§aSieg!" : "§cNiederlage";
                };
                String subtitle = winners.isEmpty() ? reason
                        : winner ? "§f" + reason : "§7Gewinner: §f" + winnerNames;
                online.sendTitle(title, subtitle, 10, 100, 20);
                online.sendMessage("§6§lRundenergebnis");
                online.sendMessage("§7Modus: §f" + getDisplayName() + " §8| §7Dauer: §f"
                        + formatDuration(Duration.between(startedAt == null ? Instant.now() : startedAt, Instant.now())));
                online.sendMessage("§7Ergebnis: " + title + " §8— §f" + reason);
                online.sendMessage(winners.isEmpty() ? "§7Es gibt keinen Gewinner."
                        : "§7Gewinner: §e" + winnerNames);
                online.playSound(online.getLocation(),
                        winner ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP
                                : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        0.9f, winner ? 1.2f : 0.8f);
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
                    long displaySeconds = Math.max(0, Math.min(60,
                            plugin.getConfig().getLong("game.result-display-seconds", 8)));
                    Runnable reset = () -> {
                        try {
                            if (plugin.getGameManager().getActiveGame().orElse(null) == game
                                    && game.getState() == GameState.RESETTING) {
                                plugin.getGameLifecycleService().completeReset();
                            }
                        } catch (RuntimeException cleanupFailure) {
                            plugin.getLogger().severe("Could not reset " + getId() + " game: "
                                    + cleanupFailure.getMessage());
                        }
                    };
                    if (displaySeconds == 0) reset.run();
                    else plugin.getServer().getScheduler().runTaskLater(plugin, reset, displaySeconds * 20L);
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Could not finish " + getId() + " game: " + exception.getMessage());
                }
            });
        }

        private String formatDuration(Duration duration) {
            long seconds = Math.max(0, duration.getSeconds());
            long minutes = seconds / 60;
            long remainder = seconds % 60;
            return minutes > 0 ? minutes + " Min. " + remainder + " Sek." : remainder + " Sek.";
        }

        protected boolean timedOut(Game game, long elapsedSeconds) {
            int configured = game.getArena().modeIntSetting(getId(), "time_limit_seconds", 0);
            int limit = configured > 0 ? configured : game.getSettings().timeLimitSeconds();
            return limit > 0 && elapsedSeconds >= limit;
        }

        protected Team team(Game game, String name, String color) {
            return game.getTeamManager().createTeam(name, color, game.getSettings().maxPlayers(), false);
        }

        protected void assign(Game game, GamePlayer player, Team team, String role) {
            game.getTeamManager().assignPlayer(player.getUniqueId(), team.getId());
            game.getRoleManager().assign(player.getUniqueId(), role, team.getId());
        }

        /**
         * Builds a role order while honoring explicit lobby team wishes first.
         * Each side gets its requested players up to capacity; neutral players
         * fill remaining places and over-subscribed wishes fall back to the other side.
         */
        protected List<GamePlayer> orderedForTeams(Game game, List<GamePlayer> source,
                                                   String primaryKey, String secondaryKey,
                                                   int primaryCapacity) {
            List<GamePlayer> shuffled = new ArrayList<>(source);
            if (game.getSettings().randomTeamDistribution()) Collections.shuffle(shuffled);
            List<GamePlayer> primaryWishes = new ArrayList<>();
            List<GamePlayer> secondaryWishes = new ArrayList<>();
            List<GamePlayer> neutral = new ArrayList<>();
            for (GamePlayer player : shuffled) {
                String preference = game.getLobbyTeamPreference(player.getUniqueId()).orElse("");
                if (preference.equals(primaryKey)) primaryWishes.add(player);
                else if (preference.equals(secondaryKey)) secondaryWishes.add(player);
                else neutral.add(player);
            }

            int secondaryCapacity = shuffled.size() - primaryCapacity;
            List<GamePlayer> primary = take(primaryWishes, primaryCapacity);
            List<GamePlayer> secondary = take(secondaryWishes, secondaryCapacity);
            List<GamePlayer> overflow = new ArrayList<>();
            overflow.addAll(primaryWishes.subList(primary.size(), primaryWishes.size()));
            overflow.addAll(secondaryWishes.subList(secondary.size(), secondaryWishes.size()));

            fill(primary, neutral, primaryCapacity);
            fill(secondary, neutral, secondaryCapacity);
            fill(primary, overflow, primaryCapacity);
            fill(secondary, overflow, secondaryCapacity);

            List<GamePlayer> ordered = new ArrayList<>(shuffled.size());
            ordered.addAll(primary);
            ordered.addAll(secondary);
            return ordered;
        }

        private List<GamePlayer> take(List<GamePlayer> source, int capacity) {
            int count = Math.min(source.size(), Math.max(0, capacity));
            return new ArrayList<>(source.subList(0, count));
        }

        private void fill(List<GamePlayer> target, List<GamePlayer> source, int capacity) {
            while (target.size() < capacity && !source.isEmpty()) {
                target.add(source.remove(0));
            }
        }

        protected long countActiveTeamMembers(Game game, Team team) {
            return team.getMembers().stream().filter(id ->
                    game.getPlayer(id).map(GamePlayer::isActiveForRound).orElse(false)).count();
        }

        protected List<UUID> activeTeamMembers(Game game, Team team) {
            return team.getMembers().stream().filter(id ->
                    game.getPlayer(id).map(GamePlayer::isActiveForRound).orElse(false)).toList();
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
            boolean randomTargets = "random".equalsIgnoreCase(
                    game.getArena().modeSetting(getId(), "target_distribution", "cycle"));
            assassins = team(game, "Assassins", "DARK_RED");
            for (int i = 0; i < players.size(); i++) {
                GamePlayer current = players.get(i);
                List<GamePlayer> candidates = players.stream()
                        .filter(candidate -> !candidate.getUniqueId().equals(current.getUniqueId())).toList();
                UUID target = randomTargets
                        ? candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size())).getUniqueId()
                        : players.get((i + 1) % players.size()).getUniqueId();
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
            UUID inheritedTarget = targets.remove(victim.getUniqueId());
            eliminate(game, victim);

            List<UUID> orphanedHunters = targets.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(victim.getUniqueId()))
                    .map(Map.Entry::getKey).toList();
            orphanedHunters.forEach(targets::remove);

            boolean validKiller = killerId != null && !killerId.equals(victim.getUniqueId())
                    && game.getPlayer(killerId).filter(p -> p.getStatus() == PlayerStatus.PLAYING).isPresent();
            if (validKiller) {
                game.getRoleManager().assign(killerId, "assassin");
                setTarget(game, killerId, inheritedTarget, "§aNeues Ziel");
            }

            for (UUID orphanedHunter : orphanedHunters) {
                if (validKiller && orphanedHunter.equals(killerId)) continue;
                setTarget(game, orphanedHunter, validKiller ? null : inheritedTarget,
                        "§eDein bisheriges Ziel ist ausgeschieden. Neues Ziel");
            }

            List<UUID> alive = activeRoundPlayers(game);
            if (alive.size() <= 1) finish(game, GameOutcome.VICTORY, alive, "Letzter Assassine übrig");
        }

        @Override public void onPlayerQuit(Game game, GamePlayer quitter) {
            UUID leavingId = quitter.getUniqueId();
            UUID inheritedTarget = targets.remove(leavingId);
            List<UUID> orphanedHunters = targets.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(leavingId))
                    .map(Map.Entry::getKey).toList();
            orphanedHunters.forEach(targets::remove);
            for (UUID hunterId : orphanedHunters) {
                setTarget(game, hunterId, inheritedTarget, "§eDein Ziel hat die Runde verlassen. Neues Ziel");
            }
            List<UUID> alive = activeRoundPlayers(game);
            if (alive.size() <= 1) finish(game, GameOutcome.VICTORY, alive, "Letzter Assassine übrig");
        }

        @Override public void onPlayerJoin(Game game, GamePlayer player) {
            super.onPlayerJoin(game, player);
            if (player.getStatus() != PlayerStatus.PLAYING) return;

            UUID target = targets.get(player.getUniqueId());
            if (target == null) {
                setTarget(game, player.getUniqueId(), null, "§aDu bist zurück! Neues Ziel");
            } else {
                sendTarget(player.getUniqueId(), target, "§aWillkommen zurück! Dein Ziel");
            }

            // A two-player round can leave the connected hunter without a
            // target while the other player is offline. Rebuild every missing
            // assignment once both participants are back in the round.
            for (UUID hunterId : activePlayers(game)) {
                if (!targets.containsKey(hunterId)) {
                    setTarget(game, hunterId, null, "§aDein Ziel wurde neu zugewiesen");
                }
            }
        }

        private void setTarget(Game game, UUID hunterId, UUID preferredTarget, String message) {
            List<UUID> candidates = activePlayers(game).stream()
                    .filter(id -> !id.equals(hunterId)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            UUID target = preferredTarget != null && candidates.contains(preferredTarget)
                    ? preferredTarget : null;
            if (target == null && !candidates.isEmpty()) {
                Collections.shuffle(candidates);
                target = candidates.get(0);
            }
            if (target == null) {
                targets.remove(hunterId);
                return;
            }
            targets.put(hunterId, target);
            sendTarget(hunterId, target, message);
        }

        private void sendTarget(UUID hunterId, UUID assignedTarget, String message) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter == null || assignedTarget == null) return;
            String name = java.util.Optional.ofNullable(Bukkit.getPlayer(assignedTarget))
                    .map(Player::getName)
                    .orElseGet(() -> java.util.Optional.ofNullable(Bukkit.getOfflinePlayer(assignedTarget).getName())
                            .orElse(assignedTarget.toString().substring(0, 8)));
            hunter.sendMessage(message + ": §c" + name);
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
            if (timedOut(game, elapsedSeconds)) finish(game, GameOutcome.TIMEOUT, activeRoundPlayers(game), "Zeitlimit erreicht");
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
            int configuredHitmen = game.getArena().modeIntSetting(getId(), "hitman_count", 0);
            int hitmanCount = configuredHitmen <= 0 ? (players.size() >= 7 ? 2 : 1)
                    : Math.min(players.size() - 1, Math.max(1, configuredHitmen));
            players = orderedForTeams(game, players, "hitmen", "survivors", hitmanCount);
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
            checkTeams(game);
        }

        @Override public void onPlayerQuit(Game game, GamePlayer quitter) {
            checkTeams(game);
        }

        private void checkTeams(Game game) {
            List<UUID> livingHitmen = activeTeamMembers(game, hitmen);
            List<UUID> livingSurvivors = activeTeamMembers(game, survivors);
            if (livingHitmen.isEmpty() && livingSurvivors.isEmpty()) {
                finish(game, GameOutcome.DRAW, List.of(), "Niemand ist mehr in der Runde");
            } else if (livingSurvivors.isEmpty()) {
                finish(game, GameOutcome.VICTORY, livingHitmen, "Alle Überlebenden ausgeschieden");
            } else if (livingHitmen.isEmpty()) {
                finish(game, GameOutcome.VICTORY, livingSurvivors, "Alle Hitmen ausgeschieden");
            }
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            if (game.getArena().modeBooleanSetting(getId(), "extraction_enabled", true)
                    && game.getArena().spawnPoint("extraction").isPresent() && game.getMapInstance().isPresent()) {
                org.bukkit.World world = plugin.getMapManager().getWorld(game.getMapInstance().orElseThrow());
                org.bukkit.Location extraction = game.getArena().spawnPoint("extraction").orElseThrow().toLocation(world);
                List<UUID> escaped = survivors.getMembers().stream()
                        .filter(id -> game.getPlayer(id).map(p -> p.getStatus() == PlayerStatus.PLAYING).orElse(false))
                        .filter(id -> {
                            Player player = Bukkit.getPlayer(id);
                            double radius = Math.max(2, Math.min(12,
                                    game.getArena().modeIntSetting(getId(), "extraction_radius", 4)));
                            return player != null && player.getWorld().equals(world)
                                    && player.getLocation().distanceSquared(extraction) <= radius * radius;
                        }).toList();
                if (!escaped.isEmpty()) {
                    finish(game, GameOutcome.VICTORY, escaped, "Überlebende haben den Extraktionspunkt erreicht");
                    return;
                }
            }
            if (timedOut(game, elapsedSeconds)) {
                List<UUID> winners = activeTeamMembers(game, survivors);
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
            players = orderedForTeams(game, players, "juggernaut", "hunters", 1);
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
                double health = Math.max(20, Math.min(60,
                        game.getArena().modeIntSetting(getId(), "boss_health", 40)));
                boss.setMaxHealth(health);
                boss.setHealth(health);
                apply(boss, PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1);
                apply(boss, PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1);
                List<org.bukkit.inventory.ItemStack> abilities = new ArrayList<>();
                if (game.getArena().modeBooleanSetting(getId(), "ability_slam", true))
                    abilities.add(JuggernautAbilityListener.abilityItem(org.bukkit.Material.BLAZE_ROD, "Ground Slam"));
                if (game.getArena().modeBooleanSetting(getId(), "ability_charge", true))
                    abilities.add(JuggernautAbilityListener.abilityItem(org.bukkit.Material.IRON_INGOT, "Charge"));
                if (game.getArena().modeBooleanSetting(getId(), "ability_roar", true))
                    abilities.add(JuggernautAbilityListener.abilityItem(org.bukkit.Material.GOAT_HORN, "Roar"));
                if (game.getArena().modeBooleanSetting(getId(), "ability_regeneration", true))
                    abilities.add(JuggernautAbilityListener.abilityItem(org.bukkit.Material.GOLDEN_APPLE, "Regeneration"));
                if (!abilities.isEmpty()) boss.getInventory().addItem(abilities.toArray(org.bukkit.inventory.ItemStack[]::new));
                boss.sendMessage("§eLeben: §c" + (int) health + " HP §7| §eAktive Fähigkeiten: §f" + abilities.size());
            }
        }

        @Override public void onPlayerDeath(Game game, GamePlayer victim, UUID killerId) {
            if (victim.isEliminated()) return;
            eliminate(game, victim);
            checkVictory(game);
        }

        @Override public void onPlayerQuit(Game game, GamePlayer quitter) {
            checkVictory(game);
        }

        private void checkVictory(Game game) {
            boolean bossAlive = game.getPlayer(bossId)
                    .map(GamePlayer::isActiveForRound).orElse(false);
            List<UUID> livingHunters = activeTeamMembers(game, hunters);
            if (!bossAlive && livingHunters.isEmpty()) {
                finish(game, GameOutcome.DRAW, List.of(), "Niemand ist mehr in der Runde");
            } else if (!bossAlive) {
                finish(game, GameOutcome.VICTORY, livingHunters, "Juggernaut ausgeschieden");
            } else if (livingHunters.isEmpty()) {
                finish(game, GameOutcome.VICTORY, List.of(bossId), "Alle Jäger ausgeschieden");
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
            int configuredInitial = game.getArena().modeIntSetting(getId(), "initial_infected", 0);
            int initial = configuredInitial <= 0 ? (players.size() >= 7 ? 2 : 1)
                    : Math.min(players.size() - 1, Math.max(1, configuredInitial));
            players = orderedForTeams(game, players, "infected", "survivors", initial);
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
                    player.sendMessage("§2Du bist jetzt infiziert! Nach dem Respawn jagst du die Überlebenden.");
                    apply(player, PotionEffectType.SPEED, 20 * 20, 0);
                }
            }
            checkTeams(game);
        }

        @Override public void onPlayerJoin(Game game, GamePlayer participant) {
            super.onPlayerJoin(game, participant);
            if (participant.getStatus() != PlayerStatus.PLAYING) return;
            if (infected.getMembers().contains(participant.getUniqueId())) {
                Player player = Bukkit.getPlayer(participant.getUniqueId());
                apply(player, PotionEffectType.SPEED, 20 * 60 * 10, 0);
                if (player != null) player.sendMessage("§2Du bist infiziert. Finde die Überlebenden!");
            } else {
                Player player = Bukkit.getPlayer(participant.getUniqueId());
                if (player != null) player.sendMessage("§bÜberlebe bis zum Zeitlimit oder entkomme!");
            }
        }

        @Override public void onPlayerQuit(Game game, GamePlayer quitter) {
            checkTeams(game);
        }

        private void checkTeams(Game game) {
            List<UUID> livingInfected = activeTeamMembers(game, infected);
            List<UUID> livingSurvivors = activeTeamMembers(game, survivors);
            if (livingInfected.isEmpty() && livingSurvivors.isEmpty()) {
                finish(game, GameOutcome.DRAW, List.of(), "Niemand ist mehr in der Runde");
            } else if (livingSurvivors.isEmpty()) {
                finish(game, GameOutcome.VICTORY, livingInfected, "Alle Überlebenden infiziert oder ausgeschieden");
            } else if (livingInfected.isEmpty()) {
                finish(game, GameOutcome.VICTORY, livingSurvivors, "Alle Infizierten ausgeschieden");
            }
        }

        @Override public void onTick(Game game, long elapsedSeconds) {
            if (timedOut(game, elapsedSeconds)) {
                List<UUID> winners = activeTeamMembers(game, survivors);
                finish(game, GameOutcome.TIMEOUT, winners, "Überlebende haben das Zeitlimit erreicht");
            }
        }
    }
}
