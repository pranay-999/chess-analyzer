package com.chessanalyzer.stats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Your statistics, computed by PostgreSQL itself with plain SQL (GROUP BY, CASE, CTEs).
 *
 * Why SQL here and not JPA entities? Loading 5,000 games into Java just to count them
 * would be slow and wasteful. The database is built for exactly this: it scans,
 * groups and counts, and sends back a few small rows.
 *
 * JdbcClient (Spring 6.1+) is a thin, readable wrapper around plain JDBC:
 *   jdbc.sql("select ...").param("u", name).query((row, i) -> ...).list()
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /**
     * Shared start of every query: "my games", each with MY colour, MY rating and MY result.
     * A game is mine if it was synced for this username, or the name played White/Black
     * (games imported by link or PGN). :tc optionally filters by time control.
     * (CAST(:tc AS varchar) tells Postgres the type even when we pass null.)
     */
    private static final String MY_GAMES = """
            WITH my AS (
              SELECT g.id, g.played_at, g.time_class, g.result, g.pgn, g.opening_eco,
                     CASE WHEN lower(g.white) = :u THEN 'WHITE'
                          WHEN lower(g.black) = :u THEN 'BLACK'
                          ELSE g.player_color END AS color,
                     CASE WHEN lower(g.white) = :u THEN g.white_rating
                          WHEN lower(g.black) = :u THEN g.black_rating END AS rating
              FROM games g LEFT JOIN players p ON p.id = g.player_id
              WHERE (p.username = :u OR lower(g.white) = :u OR lower(g.black) = :u)
                AND (CAST(:tc AS varchar) IS NULL OR g.time_class = CAST(:tc AS varchar))
            ),
            scored AS (
              SELECT my.*,
                     CASE WHEN result = '1/2-1/2' THEN 'DRAW'
                          WHEN (result = '1-0' AND color = 'WHITE') OR (result = '0-1' AND color = 'BLACK') THEN 'WIN'
                          WHEN result IN ('1-0', '0-1') THEN 'LOSS'
                          ELSE 'UNKNOWN' END AS outcome
              FROM my
            )
            """;

    /** The newest finished Stockfish analysis of each of my games (DISTINCT ON keeps the first row per game). */
    private static final String MY_ANALYSES = """
            , analysed AS (
              SELECT DISTINCT ON (a.game_id) a.id AS analysis_id, a.game_id, s.color, s.played_at,
                     CASE WHEN s.color = 'WHITE' THEN a.accuracy_white ELSE a.accuracy_black END AS accuracy
              FROM analyses a JOIN scored s ON s.id = a.game_id
              WHERE a.engine = 'stockfish' AND a.status = 'DONE'
              ORDER BY a.game_id, a.created_at DESC
            )
            """;

    /** W/L/D counts, used for every "split" query below. */
    private static final String COUNTS = """
            count(*) AS games,
            count(*) FILTER (WHERE outcome = 'WIN')  AS wins,
            count(*) FILTER (WHERE outcome = 'LOSS') AS losses,
            count(*) FILTER (WHERE outcome = 'DRAW') AS draws
            """;

    private final JdbcClient jdbc;

    public StatsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ results

    public record Split(String label, long games, long wins, long losses, long draws) {
    }

    public record Point(String month, double value, long games) {   // month = "2026-11"
    }

    public record PhaseStat(String phase, long inaccuracies, long mistakes, long blunders) {
    }

    public record Stats(String player, String timeClass, Split total, List<Split> byColor, List<Split> byTimeClass,
                        List<Split> openings, List<Point> rating, List<Point> accuracy,
                        long analysedGames, Double averageAccuracy, List<PhaseStat> mistakesByPhase) {
    }

    public Stats forPlayer(String username, String timeClass) {
        String u = username.trim().toLowerCase(Locale.ROOT);
        String tc = timeClass == null || timeClass.isBlank() ? null : timeClass;

        Split total = splits("SELECT 'All' AS label, " + COUNTS + " FROM scored", u, tc).getFirst();
        List<Split> byColor = splits("SELECT color AS label, " + COUNTS + " FROM scored GROUP BY color ORDER BY color DESC", u, tc);
        List<Split> byTimeClass = splits("SELECT coalesce(time_class, 'other') AS label, " + COUNTS
                + " FROM scored GROUP BY 1 ORDER BY games DESC", u, tc);

        // Opening name, in order of preference:
        //  Chess.com PGN: [ECOUrl "https://www.chess.com/openings/Sicilian-Defense-Alapin-Variation"]
        //                 -> up to the first Opening/Defense/Game/... word ("Sicilian Defense"),
        //                    else the first two words;
        //  Lichess PGN:   [Opening "Sicilian Defense: Alapin Variation"] -> before the ':';
        //  otherwise the ECO code (C20), or "Unknown".
        // ({0,3}? = "as few words as possible", so we stop at the FIRST such word.)
        List<Split> openings = splits("""
                SELECT opening AS label, %s FROM (
                  SELECT s.*, coalesce(
                    replace(substring(pgn from 'openings/((?:[A-Za-z]+-){0,3}?(?:Opening|Defense|Game|Gambit|Attack|System))'), '-', ' '),
                    replace(substring(pgn from 'openings/([A-Za-z]+-[A-Za-z]+)'), '-', ' '),
                    substring(pgn from '\\[Opening "([^":,]+)'),
                    opening_eco, 'Unknown') AS opening
                  FROM scored s) o
                GROUP BY opening ORDER BY games DESC, opening LIMIT 12""".formatted(COUNTS), u, tc);

        List<Point> rating = jdbc.sql(MY_GAMES + """
                        SELECT to_char(date_trunc('month', played_at), 'YYYY-MM') AS month,
                               round(avg(rating)) AS value, count(*) AS games
                        FROM scored WHERE rating IS NOT NULL GROUP BY 1 ORDER BY 1""")
                .param("u", u).param("tc", tc)
                .query((rs, i) -> new Point(rs.getString("month"), rs.getDouble("value"), rs.getLong("games")))
                .list();

        List<Point> accuracy = jdbc.sql(MY_GAMES + MY_ANALYSES + """
                        SELECT to_char(date_trunc('month', played_at), 'YYYY-MM') AS month,
                               round(avg(accuracy), 1) AS value, count(*) AS games
                        FROM analysed WHERE accuracy IS NOT NULL GROUP BY 1 ORDER BY 1""")
                .param("u", u).param("tc", tc)
                .query((rs, i) -> new Point(rs.getString("month"), rs.getDouble("value"), rs.getLong("games")))
                .list();

        long analysedGames = 0;
        double accuracySum = 0;
        for (Point p : accuracy) {
            analysedGames += p.games();
            accuracySum += p.value() * p.games();
        }

        // My own inaccuracies/mistakes/blunders, by phase of the game (from the move number).
        // My moves are the odd plies as white (1, 3, 5...) and the even plies as black.
        List<PhaseStat> phases = jdbc.sql(MY_GAMES + MY_ANALYSES + """
                        SELECT CASE WHEN (m.ply + 1) / 2 <= 10 THEN 'Opening (moves 1-10)'
                                    WHEN (m.ply + 1) / 2 <= 30 THEN 'Middlegame (11-30)'
                                    ELSE 'Endgame (31+)' END AS phase,
                               count(*) FILTER (WHERE m.classification = 'INACCURACY') AS inaccuracies,
                               count(*) FILTER (WHERE m.classification = 'MISTAKE')    AS mistakes,
                               count(*) FILTER (WHERE m.classification = 'BLUNDER')    AS blunders
                        FROM analysed a JOIN move_evaluations m ON m.analysis_id = a.analysis_id
                        WHERE (a.color = 'WHITE') = (m.ply % 2 = 1)
                        GROUP BY 1 ORDER BY min(m.ply)""")
                .param("u", u).param("tc", tc)
                .query((rs, i) -> new PhaseStat(rs.getString("phase"), rs.getLong("inaccuracies"),
                        rs.getLong("mistakes"), rs.getLong("blunders")))
                .list();

        return new Stats(username, tc, total, byColor, byTimeClass, openings, rating, accuracy,
                analysedGames, analysedGames == 0 ? null : Math.round(accuracySum / analysedGames * 10) / 10.0, phases);
    }

    private List<Split> splits(String select, String u, String tc) {
        return jdbc.sql(MY_GAMES + select)
                .param("u", u).param("tc", tc)
                .query((rs, i) -> new Split(rs.getString("label"), rs.getLong("games"), rs.getLong("wins"),
                        rs.getLong("losses"), rs.getLong("draws")))
                .list();
    }
}
