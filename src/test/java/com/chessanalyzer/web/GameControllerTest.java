package com.chessanalyzer.web;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.game.GameDetail;
import com.chessanalyzer.game.GameNotFoundException;
import com.chessanalyzer.game.GameService;
import com.chessanalyzer.game.GameSummary;
import com.chessanalyzer.game.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc sends fake HTTP requests straight into Spring MVC (no real server or port)
 * and lets us check the status code and JSON. The service is a Mockito mock, so this
 * tests ONLY the web layer: URL mapping, parameters, JSON shape, error handling.
 */
class GameControllerTest {

    private final GameService gameService = mock(GameService.class);
    private MockMvc mvc;

    private static final GameSummary SUMMARY = new GameSummary(7L, "alice", "bob", 1500, 1480,
            Color.WHITE, "1-0", "WIN", "blitz", "C20", Instant.parse("2026-11-19T10:00:00Z"));

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GameController(gameService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsGamesWithPaging() throws Exception {
        when(gameService.listGames("alice", 1, 20))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 1, 20, 21, 2));

        mvc.perform(get("/api/players/alice/games").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].outcome").value("WIN"))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void returnsGameWithMoves() throws Exception {
        var move = new GameDetail.MoveView(1, "e4", "e2e4", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        when(gameService.getGame(7L))
                .thenReturn(new GameDetail(SUMMARY, "https://www.chess.com/game/live/7", Board.START_FEN, List.of(move)));

        mvc.perform(get("/api/games/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.white").value("alice"))
                .andExpect(jsonPath("$.startFen").value(Board.START_FEN))
                .andExpect(jsonPath("$.moves[0].uci").value("e2e4"));
    }

    @Test
    void missingGameIs404ProblemDetail() throws Exception {
        when(gameService.getGame(99L)).thenThrow(new GameNotFoundException(99L));

        mvc.perform(get("/api/games/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game not found: 99"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/games/abc"))
                .andExpect(status().isBadRequest());
    }
}
