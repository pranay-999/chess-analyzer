package com.chessanalyzer.game;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A link to a game on Chess.com or Lichess, found anywhere in a piece of text.
 * Phones often share text like "Check out my game! https://www.chess.com/game/live/123?username=me",
 * so we search the text rather than expect a bare URL.
 *
 * @param site "chesscom" or "lichess"
 * @param kind for Chess.com: "live" or "daily"
 * @param id   the game id
 */
public record GameLink(String site, String kind, String id) {

    // chess.com/game/live/123, chess.com/game/daily/123, chess.com/live/game/123,
    // chess.com/daily/game/123, chess.com/analysis/game/live/123, chess.com/game/123
    private static final Pattern CHESS_COM = Pattern.compile(
            "chess\\.com/(?:analysis/)?(?:game/(live|daily)/|(live|daily)/game/|game/)(\\d+)");

    // lichess.org/HqjDTU4A, lichess.org/HqjDTU4Axxxx (player link), lichess.org/HqjDTU4A/black
    // Some Lichess pages have 8-letter names too (lichess.org/analysis, /training...): not games.
    private static final Pattern LICHESS = Pattern.compile(
            "lichess\\.org/(?:game/export/)?"
                    + "(?!(?:analysis|training|practice|tutorial|streamer|insights|password|features)(?![A-Za-z0-9]))"
                    + "([A-Za-z0-9]{8})(?:[A-Za-z0-9]{4})?(?![A-Za-z0-9])");

    public static Optional<GameLink> find(String text) {
        if (text == null) return Optional.empty();
        Matcher c = CHESS_COM.matcher(text);
        if (c.find()) {
            String kind = c.group(1) != null ? c.group(1) : c.group(2) != null ? c.group(2) : "live";
            return Optional.of(new GameLink("chesscom", kind, c.group(3)));
        }
        Matcher l = LICHESS.matcher(text);
        if (l.find()) {
            return Optional.of(new GameLink("lichess", null, l.group(1)));
        }
        return Optional.empty();
    }
}
