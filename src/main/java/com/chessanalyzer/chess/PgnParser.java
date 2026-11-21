package com.chessanalyzer.chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads PGN text, the format Chess.com gives us. A typical Chess.com PGN:
 *
 * <pre>
 * [White "alice"]
 * [Black "bob"]
 * [Result "1-0"]
 *
 * 1. e4 {[%clk 0:09:58]} 1... e5 {[%clk 0:09:57]} 2. Bc4 ... 1-0
 * </pre>
 *
 * We keep the headers, throw away comments {...}, variations (...), move
 * numbers and the result, and are left with the SAN moves in order.
 */
public final class PgnParser {

    private static final Pattern HEADER = Pattern.compile("^\\[(\\w+)\\s+\"(.*)\"\\]$");
    private static final Pattern MOVE_NUMBER = Pattern.compile("^\\d+\\.+");   // "12." or "12..."
    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2", "*");

    private PgnParser() {
    }

    public static PgnGame parse(String pgn) {
        Map<String, String> headers = new LinkedHashMap<>();   // keeps the original order
        StringBuilder moveText = new StringBuilder();

        for (String line : pgn.split("\\R")) {                 // \R = any line break
            String trimmed = line.trim();
            Matcher m = HEADER.matcher(trimmed);
            if (m.matches()) {
                headers.put(m.group(1), m.group(2));
            } else {
                moveText.append(trimmed).append(' ');
            }
        }

        List<String> sanMoves = new ArrayList<>();
        for (String token : removeCommentsAndVariations(moveText.toString()).split("\\s+")) {
            String move = MOVE_NUMBER.matcher(token).replaceFirst("");   // "1.e4" -> "e4", "1." -> ""
            if (move.isEmpty() || RESULTS.contains(move) || move.startsWith("$")) {
                continue;   // "$1" style annotations are skipped too
            }
            sanMoves.add(move);
        }
        return new PgnGame(headers, sanMoves);
    }

    /** Drops {comments} and (variations); variations may be nested. */
    private static String removeCommentsAndVariations(String text) {
        StringBuilder out = new StringBuilder();
        boolean inComment = false;
        int variationDepth = 0;
        for (char c : text.toCharArray()) {
            if (inComment) {
                if (c == '}') {
                    inComment = false;
                }
            } else if (c == '{') {
                inComment = true;
                out.append(' ');
            } else if (c == '(') {
                variationDepth++;
                out.append(' ');
            } else if (c == ')') {
                variationDepth--;
            } else if (variationDepth == 0) {
                out.append(c);
            }
        }
        return out.toString().trim();
    }
}
