package com.chessanalyzer.chesscom;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

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

    // The JSON wrappers Chess.com returns: {"archives": [...]} and {"games": [...]}
    record ArchivesResponse(List<String> archives) {
    }

    record MonthlyGamesResponse(List<ChessComGame> games) {
    }
}
