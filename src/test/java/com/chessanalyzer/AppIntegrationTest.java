package com.chessanalyzer;

import com.chessanalyzer.analysis.AnalysisStatus;
import com.chessanalyzer.analysis.AnalysisView;
import com.chessanalyzer.analysis.Classification;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chesscom.ChessComClient;
import com.chessanalyzer.chesscom.ChessComGame;
import com.chessanalyzer.chesscom.ChessComGame.ChessComPlayer;
import com.chessanalyzer.game.GameDetail;
import com.chessanalyzer.game.GameSource;
import com.chessanalyzer.game.GameSummary;
import com.chessanalyzer.game.PageResponse;
import com.chessanalyzer.game.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of the REAL application: real Spring context, real HTTP server,
 * real Flyway migrations, real Hibernate, real PostgreSQL, real @Async thread pool.
 * Only Chess.com is faked (tests must not depend on the internet).
 *
 * How the pieces connect:
 *  - @Testcontainers + @Container: before the tests, start a throw-away "postgres:17"
 *    Docker container; stop it afterwards. Needs Docker running.
 *  - @ServiceConnection: Spring Boot reads the container's random port/user/password
 *    and points spring.datasource.* at it, overriding application.yml.
 *  - @SpringBootTest(RANDOM_PORT): starts the whole app on a free port, like `mvn spring-boot:run`.
 *  - @MockitoBean: replaces the ChessComClient bean inside the app with a Mockito mock.
 *
 * Unit tests prove each class works alone; this proves they work TOGETHER
 * (SQL migrations match entities, transactions commit, async jobs finish, JSON is right).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"my-engine.max-depth=2", "my-engine.time-limit-ms=500",   // keep the engine quick
                "chess.scheduling.enabled=false"})
class AppIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    private static final String ARCHIVE = "https://api.chess.com/pub/player/alice/games/2026/11";

    @MockitoBean
    ChessComClient chessCom;

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = new TestBrowser(port).http;   // keeps cookies and sends the CSRF header, like the React app
        when(chessCom.fetchArchiveUrls("alice")).thenReturn(List.of(ARCHIVE));
        when(chessCom.fetchGames(ARCHIVE)).thenReturn(List.of(scholarsMate()));
    }

    @Test
    void importBrowseAndAnalyseAGame() throws InterruptedException {
        // 1. Import (twice: the second sync must not create duplicates).
        SyncResult first = http.post().uri("/api/players/Alice/sync").retrieve().body(SyncResult.class);
        assertEquals(1, first.imported());
        SyncResult second = http.post().uri("/api/players/alice/sync").retrieve().body(SyncResult.class);
        assertEquals(0, second.imported());
        assertEquals(1, second.alreadyStored());

        // 2. Browse the list.
        PageResponse<GameSummary> page = http.get().uri("/api/players/alice/games").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertEquals(1, page.totalElements());
        GameSummary summary = page.content().getFirst();
        assertEquals("LOSS", summary.outcome());   // alice was black and got mated

        // 3. Open the game with all its moves.
        GameDetail game = http.get().uri("/api/games/{id}", summary.id()).retrieve().body(GameDetail.class);
        assertEquals(7, game.moves().size());
        assertEquals("h5f7", game.moves().getLast().uci());

        // 4. Analyse it with our own engine (fast, and no Stockfish needed on the test machine).
        ResponseEntity<AnalysisView> started = http.post()
                .uri("/api/games/{id}/analysis?engine=my-engine&depth=2", summary.id())
                .retrieve().toEntity(AnalysisView.class);
        assertEquals(HttpStatus.ACCEPTED, started.getStatusCode());

        // 5. Poll like the browser does until the background job finishes.
        AnalysisView analysis = started.getBody();
        for (int i = 0; i < 60 && analysis.status() != AnalysisStatus.DONE && analysis.status() != AnalysisStatus.FAILED; i++) {
            Thread.sleep(500);
            analysis = http.get().uri("/api/games/{id}/analysis?engine=my-engine", summary.id())
                    .retrieve().body(AnalysisView.class);
        }
        assertEquals(AnalysisStatus.DONE, analysis.status(), "error: " + analysis.error());
        assertEquals(7, analysis.moves().size());
        assertEquals(Classification.BEST, analysis.moves().getLast().classification());   // Qxf7# is the best move
        assertNotNull(analysis.accuracyWhite());
    }

    @Test
    void pastedPgnBecomesAGameWithMoves() {
        String pgn = """
                [White "Me"]
                [Black "Friend"]
                [Result "0-1"]
                [WhiteElo "1200"]

                1. f3 e5 2. g4 Qh4# 0-1
                """;
        ResponseEntity<GameSummary> created = http.post().uri("/api/games/import")
                .body(Map.of("text", pgn, "playerColor", "WHITE"))
                .retrieve().toEntity(GameSummary.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        GameSummary summary = created.getBody();
        assertEquals(GameSource.PASTED, summary.source());
        assertEquals("LOSS", summary.outcome());
        assertEquals(1200, summary.whiteRating());

        GameDetail game = http.get().uri("/api/games/{id}", summary.id()).retrieve().body(GameDetail.class);
        assertEquals(4, game.moves().size());
        assertEquals(null, game.chesscomUrl());
    }

    @Test
    void chessComLinkIsFetchedAndYourSideDetected() {
        String pgn = "[Site \"Chess.com\"]\n[White \"bob\"]\n[Black \"alice\"]\n[Result \"0-1\"]\n\n1. f3 e5 2. g4 Qh4# 0-1\n";
        when(chessCom.fetchFinishedGamePgn("live", 4242L)).thenReturn(pgn);

        // What a phone's share menu sends: some text with the link inside.
        GameSummary summary = http.post().uri("/api/games/import")
                .body(Map.of("text", "Check out my game! https://www.chess.com/game/live/4242?username=alice",
                        "asPlayer", "alice"))
                .retrieve().body(GameSummary.class);
        assertEquals(Color.BLACK, summary.playerColor());   // alice was black -> board flipped for her
        assertEquals("WIN", summary.outcome());
    }

    @Test
    void unfinishedChessComPgnIsRefused() {
        String live = "[Site \"Chess.com\"]\n[White \"a\"]\n[Black \"b\"]\n[Result \"*\"]\n\n1. e4 e5 *\n";
        HttpClientErrorException refused = assertThrows(HttpClientErrorException.class,
                () -> http.post().uri("/api/games/import").body(Map.of("text", live)).retrieve().body(String.class));
        assertTrue(refused.getResponseBodyAs(ProblemDetail.class).getDetail().contains("still being played"));
    }

    @Test
    void pastedFenBecomesAGameStartingThere() {
        String fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1";
        GameSummary summary = http.post().uri("/api/games/import")
                .body(Map.of("text", fen)).retrieve().body(GameSummary.class);
        GameDetail game = http.get().uri("/api/games/{id}", summary.id()).retrieve().body(GameDetail.class);
        assertEquals(fen, game.startFen());
        assertEquals(0, game.moves().size());
    }

    @Test
    void badPastesGiveReadable400s() {
        HttpClientErrorException illegal = assertThrows(HttpClientErrorException.class,
                () -> http.post().uri("/api/games/import").body(Map.of("text", "1. e4 e5 2. Ke3"))
                        .retrieve().body(String.class));
        assertEquals(HttpStatus.BAD_REQUEST, illegal.getStatusCode());
        assertTrue(illegal.getResponseBodyAs(ProblemDetail.class).getDetail().startsWith("Illegal move 'Ke3'"));

        HttpClientErrorException noKing = assertThrows(HttpClientErrorException.class,
                () -> http.post().uri("/api/games/import").body(Map.of("text", "8/8/8/8/8/8/8/K7 w - - 0 1"))
                        .retrieve().body(String.class));
        assertEquals(HttpStatus.BAD_REQUEST, noKing.getStatusCode());
    }

    @Test
    void errorsAreProblemDetailJson() {
        HttpClientErrorException notFound = assertThrows(HttpClientErrorException.class,
                () -> http.get().uri("/api/games/999999").retrieve().body(String.class));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("Game not found: 999999", notFound.getResponseBodyAs(ProblemDetail.class).getDetail());

        HttpClientErrorException badEngine = assertThrows(HttpClientErrorException.class,
                () -> http.post().uri("/api/games/1/analysis?engine=nope").retrieve().body(String.class));
        assertEquals(HttpStatus.BAD_REQUEST, badEngine.getStatusCode());
    }

    @Test
    void reactAppIsServedForPageUrls() {
        // index.html exists only after `npm run build`; then both "/" and "/games/1" return it.
        // Without a frontend build Spring answers 404 for both, which is also consistent.
        HttpStatus home = statusOf("/");
        assertEquals(home, statusOf("/games/1"));
    }

    private HttpStatus statusOf(String path) {
        Integer code = http.get().uri(path).exchange((req, res) -> res.getStatusCode().value());
        return HttpStatus.valueOf(code);
    }

    private static ChessComGame scholarsMate() {
        String pgn = """
                [White "bob"]
                [Black "alice"]
                [Result "1-0"]
                [ECO "C23"]

                1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
                """;
        return new ChessComGame("https://www.chess.com/game/live/1", pgn, "blitz", "chess", 1763650000L,
                new ChessComPlayer("bob", 1500, "win"), new ChessComPlayer("alice", 1480, "checkmated"));
    }
}
