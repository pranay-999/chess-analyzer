package com.chessanalyzer.lichess;

import com.chessanalyzer.chesscom.GameInProgressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LichessClientTest {

    private MockRestServiceServer server;
    private LichessClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LichessClient(builder);
    }

    @Test
    void fetchesFinishedGame() {
        server.expect(requestTo("https://lichess.org/game/export/AbCd1234?clocks=false&evals=false"))
                .andExpect(header("Accept", "application/x-chess-pgn"))
                .andRespond(withSuccess("[White \"a\"]\n[Black \"b\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n",
                        MediaType.parseMediaType("application/x-chess-pgn")));

        assertTrue(client.fetchFinishedGamePgn("AbCd1234").contains("1. e4 e5"));
    }

    @Test
    void refusesGameInProgress() {
        server.expect(requestTo("https://lichess.org/game/export/Live1234?clocks=false&evals=false"))
                .andRespond(withSuccess("[Result \"*\"]\n\n1. e4 *\n", MediaType.parseMediaType("application/x-chess-pgn")));

        assertThrows(GameInProgressException.class, () -> client.fetchFinishedGamePgn("Live1234"));
    }

    @Test
    void unknownGame() {
        server.expect(requestTo("https://lichess.org/game/export/Nope1234?clocks=false&evals=false"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(IllegalArgumentException.class, () -> client.fetchFinishedGamePgn("Nope1234"));
    }
}
