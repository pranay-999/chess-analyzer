package com.chessanalyzer.chesscom;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Talks to the public Chess.com API (https://www.chess.com/news/view/published-data-api).
 * No login or API key is needed.
 *
 *   GET /pub/player/{user}/games/archives  -> list of monthly archive URLs
 *   GET /pub/player/{user}/games/2026/11   -> all games of that month (with PGN)
 *
 * @Component makes this a Spring bean, so services can receive it in their constructor.
 * Keeping all HTTP details in this one class means the rest of the app never
 * sees URLs or JSON — only Java objects.
 */
@Component
public class ChessComClient {

    private static final String BASE_URL = "https://api.chess.com/pub";

    private final RestClient restClient;

    /**
     * Spring Boot provides a pre-configured RestClient.Builder (already knows how to
     * turn JSON into objects). We add our base URL and a User-Agent header, which
     * Chess.com asks API users to send.
     */
    public ChessComClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "chess-analyzer (personal learning project)")
                .build();
    }

    public List<String> fetchArchiveUrls(String username) {
        try {
            ArchivesResponse response = restClient.get()
                    .uri("/player/{username}/games/archives", username)   // {username} is filled in and URL-encoded
                    .retrieve()                                            // throws on 4xx/5xx
                    .body(ArchivesResponse.class);                         // JSON -> record
            return response == null ? List.of() : response.archives();
        } catch (HttpClientErrorException.NotFound e) {
            throw new PlayerNotFoundException(username);
        }
    }

    /** @param archiveUrl a full URL taken from fetchArchiveUrls(), e.g. ".../games/2026/11" */
    public List<ChessComGame> fetchGames(String archiveUrl) {
        MonthlyGamesResponse response = restClient.get()
                .uri(URI.create(archiveUrl))
                .retrieve()
                .body(MonthlyGamesResponse.class);
        return response == null ? List.of() : response.games();
    }

    /**
     * The PGN of ONE game, from its link (e.g. chess.com/game/live/187517802954).
     *
     * The public API has no "game by id" endpoint, so we combine two calls:
     *  1. the endpoint chess.com's own game page uses (/callback/{live|daily}/game/{id}): is the
     *     game finished, who played, when did it end?  It's unofficial, so it may change some day;
     *  2. that player's monthly archive from the official public API, which only contains
     *     FINISHED games; we pick the one whose URL ends with the id.
     *
     * A game still in progress is refused: analysing a live game with an engine is cheating.
     */
    public String fetchFinishedGamePgn(String kind, long gameId) {
        CallbackResponse info;
        try {
            info = restClient.get()
                    .uri(URI.create("https://www.chess.com/callback/" + kind + "/game/" + gameId))
                    .retrieve()
                    .body(CallbackResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Chess.com game " + gameId + " not found");
        }
        if (info == null || info.game() == null) {
            throw new IllegalArgumentException("Chess.com didn't return game " + gameId);
        }
        if (!info.game().isFinished()) {
            throw new GameInProgressException();
        }

        Map<String, String> headers = info.game().pgnHeaders();
        YearMonth month = info.game().endTime() != null
                ? YearMonth.from(Instant.ofEpochSecond(info.game().endTime()).atZone(ZoneOffset.UTC))
                : YearMonth.parse(headers.get("Date").substring(0, 7).replace('.', '-'));
        String suffix = "/" + gameId;
        for (String player : List.of(headers.get("White"), headers.get("Black"))) {
            String archive = String.format("https://api.chess.com/pub/player/%s/games/%d/%02d",
                    player.toLowerCase(), month.getYear(), month.getMonthValue());
            for (ChessComGame g : fetchGames(archive)) {
                if (g.url() != null && g.url().endsWith(suffix) && g.pgn() != null) {
                    return g.pgn();
                }
            }
        }
        throw new IllegalArgumentException(
                "Couldn't find this game in Chess.com's archive yet. It can take a minute after a game ends. "
                        + "Try again, or share the PGN instead of the link.");
    }

    // The JSON wrappers Chess.com returns: {"archives": [...]} and {"games": [...]}
    record ArchivesResponse(List<String> archives) {
    }

    record MonthlyGamesResponse(List<ChessComGame> games) {
    }

    // Only the fields we need from the /callback/... answer (Jackson ignores the rest).
    record CallbackResponse(CallbackGame game) {
    }

    record CallbackGame(boolean isFinished, Long endTime, Map<String, String> pgnHeaders) {
    }
}
