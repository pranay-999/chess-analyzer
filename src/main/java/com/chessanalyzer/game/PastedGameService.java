package com.chessanalyzer.game;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.PgnGame;
import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.chess.PlayedMove;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * "Analysis board": turns pasted text into a stored Game, so the normal game page
 * (replay, Stockfish analysis, drag-to-explore) works on it with no extra code.
 *
 *  - A PGN becomes a game with all its moves.
 *  - A FEN becomes a game with ZERO moves that starts from that position
 *    (stored as a tiny PGN with a [FEN "..."] header, which our PgnParser already understands).
 *
 * Bad input throws IllegalArgumentException, which ApiExceptionHandler turns into
 * HTTP 400 with a readable message ("Illegal move 'Ke3' in position ...").
 */
@Service
public class PastedGameService {

    static final int MAX_LENGTH = 100_000;
    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2");
    private static final DateTimeFormatter PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final GameRepository games;

    public PastedGameService(GameRepository games) {
        this.games = games;
    }

    /** @param playerColor which side "you" were (decides WIN/LOSS and board orientation); WHITE if null */
    public GameSummary importText(String text, Color playerColor) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paste a PGN or a FEN first");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("That's too long for a single game");
        }
        Color color = playerColor == null ? Color.WHITE : playerColor;
        Game game = looksLikeFen(text) ? fromFen(text.trim(), color) : fromPgn(text, color);
        return GameSummary.from(games.save(game));   // save cascades to the moves
    }

    /** A FEN's first field has exactly 7 slashes ("rnbqkbnr/pppppppp/8/.../RNBQKBNR"); a PGN has [tags] or move numbers. */
    static boolean looksLikeFen(String text) {
        String firstField = text.trim().split("\\s+")[0];
        return !text.contains("[") && firstField.chars().filter(c -> c == '/').count() == 7;
    }

    private Game fromFen(String fen, Color color) {
        Board board = Board.fromFen(fen);   // also rejects positions without exactly one king per side
        String pgn = "[SetUp \"1\"]\n[FEN \"" + board.toFen() + "\"]\n\n*\n";
        return Game.pasted(pgn, "White", "Black", null, null, color, "*", null, Instant.now());
    }

    private Game fromPgn(String text, Color color) {
        if (text.split("\\[Event ", -1).length > 2) {
            throw new IllegalArgumentException("Paste one game at a time");
        }
        PgnGame pgn = PgnParser.parse(text);
        String variant = pgn.header("Variant");
        if (variant != null && !variant.equalsIgnoreCase("Standard")) {
            throw new IllegalArgumentException("Only standard chess is supported (this game is " + variant + ")");
        }
        if (pgn.sanMoves().isEmpty() && pgn.header("FEN") == null) {
            throw new IllegalArgumentException("No moves found. Paste a PGN (e.g. \"1. e4 e5 2. Nf3\") or a FEN");
        }
        List<PlayedMove> played = pgn.replay();   // throws IllegalArgumentException on an illegal move

        String result = pgn.header("Result");
        Game game = Game.pasted(text.trim(),
                nameOr(pgn.header("White"), "White"), nameOr(pgn.header("Black"), "Black"),
                rating(pgn.header("WhiteElo")), rating(pgn.header("BlackElo")),
                color, RESULTS.contains(result) ? result : "*", pgn.header("ECO"), playedAt(pgn.header("Date")));
        for (PlayedMove m : played) {
            game.addMove(new GameMove(m.ply(), m.san(), m.uci(), m.fenAfter()));
        }
        return game;
    }

    private static String nameOr(String value, String fallback) {
        return value == null || value.isBlank() || value.equals("?") ? fallback : value;
    }

    private static Integer rating(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;   // "?" or garbage: just leave it empty
        }
    }

    /** PGN dates look like "2024.01.15" (unknown parts are "??"). Fall back to "now". */
    private static Instant playedAt(String date) {
        try {
            return date == null ? Instant.now() : LocalDate.parse(date, PGN_DATE).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}
