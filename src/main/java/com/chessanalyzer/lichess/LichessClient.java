package com.chessanalyzer.lichess;

import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.chesscom.GameInProgressException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Lichess's official, public API (https://lichess.org/api): one call returns a game's PGN.
 *   GET https://lichess.org/game/export/{id}   (Accept: application/x-chess-pgn)
 */
@Component
public class LichessClient {

    private final RestClient restClient;

    public LichessClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://lichess.org")
                .defaultHeader(HttpHeaders.USER_AGENT, "chess-analyzer (personal learning project)")
                .build();
    }

    /** @param gameId the 8-character id from a link like lichess.org/HqjDTU4A */
    public String fetchFinishedGamePgn(String gameId) {
        String pgn;
        try {
            pgn = restClient.get()
                    .uri("/game/export/{id}?clocks=false&evals=false", gameId)
                    .header(HttpHeaders.ACCEPT, "application/x-chess-pgn")
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Lichess game " + gameId + " not found");
        }
        if (pgn == null || pgn.isBlank()) {
            throw new IllegalArgumentException("Lichess returned no game for " + gameId);
        }
        // A game in progress has the result "*". Refuse it (fair play), like on Chess.com.
        if ("*".equals(PgnParser.parse(pgn).header("Result"))) {
            throw new GameInProgressException();
        }
        return pgn;
    }
}
