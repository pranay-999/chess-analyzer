package com.chessanalyzer.chess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Known opening theory: about 3,500 named lines from the free Lichess list
 * (src/main/resources/openings/a.tsv … e.tsv, public domain, lichess-org/chess-openings).
 * Each line looks like:   C50 ⇥ Italian Game ⇥ 1. e4 e5 2. Nf3 Nc6 3. Bc4
 *
 * We play every line once and remember each POSITION it passes through. Why positions
 * and not move lists? Transpositions: 1. d4 Nf6 2. c4 e6 and 1. c4 e6 2. d4 Nf6 reach
 * the same position, and both are theory.
 *
 * A position's key is the FEN's first three fields: pieces, side to move, castling rights.
 * We drop the move counters (they differ between transpositions) and the en-passant
 * square: our FENs write it after EVERY double pawn push, so 1. c4 e6 2. d4 d5 would
 * have "d6" while 1. d4 d5 2. c4 e6 has "-", for the same position.
 *
 * Plain Java, no Spring: the data is loaded the first time anyone asks (the "holder"
 * idiom: the JVM runs a class's static initialiser exactly once, thread-safely, on first use).
 */
public final class OpeningBook {

    private OpeningBook() {
    }

    /** True if this position appears in any known opening line. */
    public static boolean contains(String fen) {
        return Data.POSITIONS.contains(key(fen));
    }

    /** The name of the opening that ENDS in this position, e.g. "Italian Game: Two Knights Defense". */
    public static Optional<String> nameOf(String fen) {
        return Optional.ofNullable(Data.NAMES.get(key(fen)));
    }

    static String key(String fen) {
        String[] parts = fen.split(" ");
        return parts.length < 3 ? fen : String.join(" ", parts[0], parts[1], parts[2]);
    }

    /** Loaded on first use (see class comment). */
    private static final class Data {
        static final Set<String> POSITIONS = new HashSet<>();
        static final Map<String, String> NAMES = new HashMap<>();

        static {
            for (String file : new String[] {"a", "b", "c", "d", "e"}) {
                load("/openings/" + file + ".tsv");
            }
        }

        private static void load(String resource) {
            try (InputStream in = OpeningBook.class.getResourceAsStream(resource)) {
                if (in == null) throw new IllegalStateException("Missing " + resource);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.readLine(); // header: eco, name, pgn
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    String[] cols = line.split("\t");
                    if (cols.length == 3) addLine(cols[1], cols[2]);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void addLine(String name, String moves) {
            Board board = Board.startingPosition();
            POSITIONS.add(key(board.toFen()));
            for (String token : moves.split(" ")) {
                if (token.endsWith(".")) continue; // "1." move numbers
                board = board.makeMove(San.fromSan(board, token));
                POSITIONS.add(key(board.toFen()));
            }
            // The list goes from general to specific, so the first name for a position is the main one.
            NAMES.putIfAbsent(key(board.toFen()), name);
        }
    }
}
