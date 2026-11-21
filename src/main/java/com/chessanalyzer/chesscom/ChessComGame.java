package com.chessanalyzer.chesscom;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object): the shape of one game in Chess.com's JSON.
 * Jackson (the JSON library) fills this record from the response. Fields we don't
 * list here (accuracies, fen, tcn...) are simply ignored.
 *
 * Chess.com uses snake_case ("time_class"); @JsonProperty maps it to our camelCase names.
 */
public record ChessComGame(
        String url,
        String pgn,
        @JsonProperty("time_class") String timeClass,
        String rules,                                    // "chess", "chess960", "bughouse", ...
        @JsonProperty("end_time") long endTime,          // seconds since 1970 (Unix time)
        ChessComPlayer white,
        ChessComPlayer black) {

    public record ChessComPlayer(String username, Integer rating, String result) {
    }
}
