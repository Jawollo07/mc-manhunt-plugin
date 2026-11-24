package de.jawollo07.manhunt.stats;

import de.jawollo07.manhunt.game.Game;
import de.jawollo07.manhunt.game.GameOutcome;
import de.jawollo07.manhunt.game.GameResult;
import de.jawollo07.manhunt.storage.StorageManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable, idempotent game history and player statistics backed by SQLite.
 * Cancelled and aborted rounds are retained in history but excluded from rankings.
 */
public final class GameStatisticsRepository {
    private final StorageManager storage;

    public GameStatisticsRepository(StorageManager storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public void recordGame(Game game, GameResult result) throws SQLException {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(result, "result");
        if (!game.getId().equals(result.gameId())) {
            throw new IllegalArgumentException("Result belongs to a different game");
        }

        try (Connection connection = storage.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT OR IGNORE INTO game_history
                        (game_id, mode_id, arena_id, outcome, started_at, ended_at, duration_seconds, end_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    insert.setString(1, result.gameId().toString());
                    insert.setString(2, game.getMode().getId());
                    insert.setString(3, game.getArena().id());
                    insert.setString(4, result.outcome().name());
                    insert.setLong(5, result.startedAt().toEpochMilli());
                    insert.setLong(6, result.endedAt().toEpochMilli());
                    insert.setLong(7, Math.max(0L, result.duration().toSeconds()));
                    insert.setString(8, result.endReason() == null ? "" : result.endReason());
                    insert.executeUpdate();
                }

                try (PreparedStatement participant = connection.prepareStatement("""
                        INSERT OR IGNORE INTO game_participants
                        (game_id, player_uuid, is_winner)
                        VALUES (?, ?, ?)
                        """)) {
                    for (UUID playerId : result.participants()) {
                        participant.setString(1, result.gameId().toString());
                        participant.setString(2, playerId.toString());
                        participant.setInt(3, result.winners().contains(playerId) ? 1 : 0);
                        participant.addBatch();
                    }
                    participant.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<PlayerStatistics> getPlayerStatistics(UUID playerId, String modeId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        String modeFilter = modeId == null ? "" : " AND h.mode_id = ?";
        String sql = """
                SELECT COUNT(*) AS games_played,
                       COALESCE(SUM(CASE WHEN p.is_winner = 1 THEN 1 ELSE 0 END), 0) AS wins,
                       COALESCE(SUM(CASE WHEN p.is_winner = 0 AND h.outcome <> 'DRAW' THEN 1 ELSE 0 END), 0) AS losses,
                       COALESCE(SUM(CASE WHEN h.outcome = 'DRAW' THEN 1 ELSE 0 END), 0) AS draws,
                       COALESCE(SUM(h.duration_seconds), 0) AS total_seconds
                FROM game_participants p
                JOIN game_history h ON h.game_id = p.game_id
                WHERE p.player_uuid = ?
                  AND h.outcome NOT IN ('CANCELLED', 'ABORTED')
                """ + modeFilter;
        try (Connection connection = storage.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, playerId.toString());
            if (modeId != null) query.setString(2, modeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || row.getLong("games_played") == 0) return Optional.empty();
                return Optional.of(new PlayerStatistics(
                        playerId, modeId, row.getLong("games_played"), row.getLong("wins"),
                        row.getLong("losses"), row.getLong("draws"), row.getLong("total_seconds")
                ));
            }
        }
    }

    public List<LeaderboardEntry> getLeaderboard(String modeId, int limit) throws SQLException {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Leaderboard limit must be 1..100");
        String modeFilter = modeId == null ? "" : " AND h.mode_id = ?";
        String sql = """
                SELECT p.player_uuid,
                       COUNT(*) AS games_played,
                       SUM(CASE WHEN p.is_winner = 1 THEN 1 ELSE 0 END) AS wins,
                       SUM(CASE WHEN p.is_winner = 0 AND h.outcome <> 'DRAW' THEN 1 ELSE 0 END) AS losses,
                       SUM(CASE WHEN h.outcome = 'DRAW' THEN 1 ELSE 0 END) AS draws,
                       SUM(h.duration_seconds) AS total_seconds
                FROM game_participants p
                JOIN game_history h ON h.game_id = p.game_id
                WHERE h.outcome NOT IN ('CANCELLED', 'ABORTED')
                """ + modeFilter + """
                GROUP BY p.player_uuid
                ORDER BY wins DESC,
                         games_played DESC,
                         p.player_uuid ASC
                LIMIT ?
                """;
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = storage.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            int index = 1;
            if (modeId != null) query.setString(index++, modeId);
            query.setInt(index, limit);
            try (ResultSet rows = query.executeQuery()) {
                int rank = 1;
                while (rows.next()) {
                    entries.add(new LeaderboardEntry(
                            rank++, UUID.fromString(rows.getString("player_uuid")),
                            rows.getLong("games_played"), rows.getLong("wins"),
                            rows.getLong("losses"), rows.getLong("draws"),
                            rows.getLong("total_seconds")
                    ));
                }
            }
        }
        return List.copyOf(entries);
    }

    public List<GameHistoryEntry> getRecentHistory(UUID playerId, int limit) throws SQLException {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("History limit must be 1..100");
        String participantFilter = playerId == null ? "" : """
                 AND EXISTS (SELECT 1 FROM game_participants p
                             WHERE p.game_id = h.game_id AND p.player_uuid = ?)
                """;
        String sql = """
                SELECT h.game_id, h.mode_id, h.arena_id, h.outcome, h.started_at,
                       h.ended_at, h.duration_seconds, h.end_reason
                FROM game_history h
                WHERE 1 = 1
                """ + participantFilter + """
                ORDER BY h.ended_at DESC
                LIMIT ?
                """;
        List<GameHistoryEntry> history = new ArrayList<>();
        try (Connection connection = storage.getConnection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            int index = 1;
            if (playerId != null) query.setString(index++, playerId.toString());
            query.setInt(index, limit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    history.add(new GameHistoryEntry(
                            UUID.fromString(rows.getString("game_id")), rows.getString("mode_id"),
                            rows.getString("arena_id"), rows.getString("outcome"),
                            Instant.ofEpochMilli(rows.getLong("started_at")),
                            Instant.ofEpochMilli(rows.getLong("ended_at")),
                            rows.getLong("duration_seconds"), rows.getString("end_reason")
                    ));
                }
            }
        }
        return List.copyOf(history);
    }
}
