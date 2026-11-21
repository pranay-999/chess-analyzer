package com.chessanalyzer;

import com.chessanalyzer.game.GameDetail;
import com.chessanalyzer.online.LobbyEntry;
import com.chessanalyzer.online.OnlineGameView;
import com.chessanalyzer.online.OnlineStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accounts and online play, end to end: real app, real Postgres, real Spring Security
 * (sessions + CSRF), real WebSocket. Two TestBrowsers play each other.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "chess.scheduling.enabled=false")   // the clock checker isn't needed here
class OnlinePlayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @LocalServerPort
    int port;

    record Me(Map<String, Object> user) {
    }

    @Test
    void signupLoginLogoutAndGuest() {
        TestBrowser browser = new TestBrowser(port);
        assertNull(me(browser));

        // Online play needs a login: 401, not a redirect to some HTML page.
        HttpClientErrorException anonymous = assertThrows(HttpClientErrorException.class,
                () -> browser.http.get().uri("/api/online/games/open").retrieve().body(String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatusCode());

        signup(browser, "carol", "secret-pass");
        assertEquals("carol", me(browser).get("username"));

        browser.http.post().uri("/api/auth/logout").retrieve().toBodilessEntity();
        assertNull(me(browser));

        HttpClientErrorException wrong = assertThrows(HttpClientErrorException.class,
                () -> login(browser, "carol", "nope-nope"));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatusCode());

        login(browser, "Carol", "secret-pass");   // usernames are case-insensitive
        assertEquals("carol", me(browser).get("username"));

        HttpClientErrorException taken = assertThrows(HttpClientErrorException.class,
                () -> signup(new TestBrowser(port), "CAROL", "another-pass"));
        assertEquals(HttpStatus.CONFLICT, taken.getStatusCode());

        TestBrowser guest = new TestBrowser(port);
        guest.http.post().uri("/api/auth/guest").retrieve().toBodilessEntity();
        assertTrue(((String) me(guest).get("username")).startsWith("Guest-"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() {
        // A plain client that doesn't send X-XSRF-TOKEN, like a forged request from another site.
        HttpClientErrorException forbidden = assertThrows(HttpClientErrorException.class,
                () -> org.springframework.web.client.RestClient.create("http://localhost:" + port)
                        .post().uri("/api/auth/guest").retrieve().toBodilessEntity());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
    }

    @Test
    void twoPlayersPlayAGameWithLiveUpdates() throws Exception {
        TestBrowser alice = new TestBrowser(port);
        signup(alice, "alice", "alice-pass");
        TestBrowser bob = new TestBrowser(port);
        bob.http.post().uri("/api/auth/guest").retrieve().toBodilessEntity();

        // Alice opens a 5-minute game as white.
        OnlineGameView created = alice.http.post().uri("/api/online/games")
                .body(Map.of("minutes", 5, "increment", 0, "color", "WHITE", "listed", true))
                .retrieve().body(OnlineGameView.class);
        String id = created.id();
        assertEquals(OnlineStatus.WAITING, created.status());

        // Bob sees it in the lobby, as black for him.
        List<LobbyEntry> lobby = bob.http.get().uri("/api/online/games/open").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        LobbyEntry entry = lobby.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        assertEquals("BLACK", entry.joinerColor().name());

        // Bob listens on the WebSocket before joining.
        BlockingQueue<Map<String, Object>> pushed = subscribe(bob, "/topic/games/" + id);

        OnlineGameView joined = bob.http.post().uri("/api/online/games/{id}/join", id).retrieve().body(OnlineGameView.class);
        assertEquals(OnlineStatus.ACTIVE, joined.status());
        assertEquals(300_000L, joined.whiteMs(), 2_000);
        assertEquals("ACTIVE", pushed.poll(5, TimeUnit.SECONDS).get("status"));

        // It's white's turn: Bob (black) may not move.
        HttpClientErrorException notYourTurn = assertThrows(HttpClientErrorException.class,
                () -> move(bob, id, "e7e5"));
        assertEquals("It's not your turn", notYourTurn.getResponseBodyAs(ProblemDetail.class).getDetail());

        move(alice, id, "f2f3");
        Map<String, Object> afterF3 = pushed.poll(5, TimeUnit.SECONDS);   // Bob is told instantly
        assertEquals(1, ((List<?>) afterF3.get("moves")).size());

        HttpClientErrorException illegal = assertThrows(HttpClientErrorException.class, () -> move(bob, id, "e7e4"));
        assertEquals(HttpStatus.BAD_REQUEST, illegal.getStatusCode());

        move(bob, id, "e7e5");
        move(alice, id, "g2g4");
        OnlineGameView end = move(bob, id, "d8h4");   // Qh4#
        assertEquals(OnlineStatus.FINISHED, end.status());
        assertEquals("0-1", end.result());
        assertEquals("checkmate", end.reason());

        // The finished game was copied into "games", so it opens in the analyser.
        assertNotNull(end.analysisGameId());
        GameDetail stored = alice.http.get().uri("/api/games/{id}", end.analysisGameId()).retrieve().body(GameDetail.class);
        assertEquals(4, stored.moves().size());
        assertEquals("alice", stored.summary().white());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> me(TestBrowser browser) {
        return browser.http.get().uri("/api/auth/me").retrieve().body(Me.class).user();
    }

    private void signup(TestBrowser browser, String username, String password) {
        browser.http.post().uri("/api/auth/signup").body(Map.of("username", username, "password", password))
                .retrieve().toBodilessEntity();
    }

    private void login(TestBrowser browser, String username, String password) {
        browser.http.post().uri("/api/auth/login").body(Map.of("username", username, "password", password))
                .retrieve().toBodilessEntity();
    }

    private OnlineGameView move(TestBrowser browser, String id, String uci) {
        return browser.http.post().uri("/api/online/games/{id}/moves", id).body(Map.of("uci", uci))
                .retrieve().body(OnlineGameView.class);
    }

    /** Opens a STOMP-over-WebSocket connection as this browser (its session cookie) and collects messages. */
    private BlockingQueue<Map<String, Object>> subscribe(TestBrowser browser, String topic) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + browser.cookie("JSESSIONID").orElseThrow());

        StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders h, Object payload) {
                messages.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300);   // let the SUBSCRIBE frame reach the server before anything is published
        return messages;
    }
}
