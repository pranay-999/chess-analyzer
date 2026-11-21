package com.chessanalyzer.game;

import com.chessanalyzer.chess.Color;

import java.time.Instant;

/**
 * One row of the "my games" list, as JSON. A response DTO: we never send entities
 * straight to the browser, because that would expose every field (and lazy
 * relations can blow up during JSON conversion).
 *
 * @param outcome WIN / LOSS / DRAW from YOUR point of view
 */
public record GameSummary(long id, String white, String black, Integer whiteRating, Integer blackRating,
                          Color playerColor, String result, String outcome, String timeClass,
                          String openingEco, Instant playedAt) {

    public static GameSummary from(Game g) {
        return new GameSummary(g.getId(), g.getWhite(), g.getBlack(), g.getWhiteRating(), g.getBlackRating(),
                g.getPlayerColor(), g.getResult(), outcome(g.getResult(), g.getPlayerColor()),
                g.getTimeClass(), g.getOpeningEco(), g.getPlayedAt());
    }

    static String outcome(String result, Color playerColor) {
        return switch (result) {
            case "1/2-1/2" -> "DRAW";
            case "1-0" -> playerColor == Color.WHITE ? "WIN" : "LOSS";
            case "0-1" -> playerColor == Color.BLACK ? "WIN" : "LOSS";
            default -> "UNKNOWN";
        };
    }
}
