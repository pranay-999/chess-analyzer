package com.chessanalyzer.chesscom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the HTTP client WITHOUT the internet: MockRestServiceServer sits inside the
 * RestClient and answers requests with canned JSON. We check that we call the right
 * URL and turn the JSON into the right Java objects.
 */
class ChessComClientTest {

    private MockRestServiceServer server;
    private ChessComClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ChessComClient(builder);
    }

    @Test
    void fetchesArchiveList() {
        server.expect(requestTo("https://api.chess.com/pub/player/alice/games/archives"))
                .andExpect(header("User-Agent", "chess-analyzer (personal learning project)"))
                .andRespond(withSuccess("""
                        {"archives": ["https://api.chess.com/pub/player/alice/games/2026/10",
                                      "https://api.chess.com/pub/player/alice/games/2026/11"]}
                        """, MediaType.APPLICATION_JSON));

        List<String> archives = client.fetchArchiveUrls("alice");

        assertEquals(2, archives.size());
        assertEquals("https://api.chess.com/pub/player/alice/games/2026/11", archives.get(1));
        server.verify();
    }

    @Test
    void unknownPlayerBecomesPlayerNotFound() {
        server.expect(requestTo("https://api.chess.com/pub/player/nobody/games/archives"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(PlayerNotFoundException.class, () -> client.fetchArchiveUrls("nobody"));
    }

    @Test
    void parsesGamesJson() {
        String url = "https://api.chess.com/pub/player/alice/games/2026/11";
        server.expect(requestTo(url)).andRespond(withSuccess("""
                {"games": [{
                    "url": "https://www.chess.com/game/live/1",
                    "pgn": "[White \\"alice\\"]\\n\\n1. e4 e5 *",
                    "time_class": "blitz",
                    "rules": "chess",
                    "end_time": 1763650000,
                    "rated": true,
                    "white": {"username": "alice", "rating": 1500, "result": "win"},
                    "black": {"username": "bob", "rating": 1480, "result": "resigned"}
                }]}
                """, MediaType.APPLICATION_JSON));

        List<ChessComGame> games = client.fetchGames(url);

        ChessComGame g = games.get(0);
        assertEquals("blitz", g.timeClass());
        assertEquals(1763650000L, g.endTime());
        assertEquals("bob", g.black().username());
        assertEquals(1500, g.white().rating());
        assertEquals("[White \"alice\"]\n\n1. e4 e5 *", g.pgn());
    }
}
