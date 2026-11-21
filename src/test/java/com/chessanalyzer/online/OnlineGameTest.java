package com.chessanalyzer.online;

import com.chessanalyzer.chess.Color;
import com.chessanalyzer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of OnlineGame without any database: we pass the "current time" in
 * ourselves (Instant), so clock tests are exact and instant instead of sleeping.
 */
class OnlineGameTest {

    private final User alice = user(1L, "alice");
    private final User bob = user(2L, "bob");
    private final Instant t0 = Instant.parse("2026-11-21T10:00:00Z");
    private OnlineGame game;

    @BeforeEach
    void setUp() {
        // 1 minute + 2 seconds increment; alice is white.
        game = OnlineGame.create("abc", alice, Color.WHITE, 60_000L, 2_000L, true);
        game.join(bob, t0);
    }

    @Test
    void clockChargesThinkingTimeAndAddsIncrement() {
        game.play(alice, "e2e4", t0.plusSeconds(10));                       // white thought 10 s
        assertEquals(52_000, game.remaining(Color.WHITE, t0.plusSeconds(10)));   // 60 - 10 + 2
        assertEquals(60_000, game.remaining(Color.BLACK, t0.plusSeconds(10)));   // black's clock just started
        assertEquals(55_000, game.remaining(Color.BLACK, t0.plusSeconds(15)));   // ...and is ticking
        assertEquals(52_000, game.remaining(Color.WHITE, t0.plusSeconds(15)));   // white's is stopped
    }

    @Test
    void movingAfterTheFlagLosesOnTime() {
        game.play(alice, "e2e4", t0.plusSeconds(61));   // 61 s > 60 s
        assertEquals(OnlineStatus.FINISHED, game.getStatus());
        assertEquals("0-1", game.getResult());
        assertEquals("timeout", game.getReason());
        assertTrue(game.uciMoves().isEmpty());          // the late move doesn't count
    }

    @Test
    void clockCheckerFlagsAnIdlePlayer() {
        assertFalse(game.flagIfTimeUp(t0.plusSeconds(59)));
        assertTrue(game.flagIfTimeUp(t0.plusSeconds(60)));
        assertEquals("0-1", game.getResult());
    }

    @Test
    void onlyThePlayerToMoveMayMove() {
        User stranger = user(3L, "eve");
        assertThrows(IllegalArgumentException.class, () -> game.play(bob, "e7e5", t0));       // not your turn
        assertThrows(IllegalArgumentException.class, () -> game.play(stranger, "e2e4", t0));  // not a player
        assertThrows(IllegalArgumentException.class, () -> game.play(alice, "e2e5", t0));     // illegal move
    }

    @Test
    void resignAndCannotJoinOwnGame() {
        OnlineGame waiting = OnlineGame.create("xyz", alice, Color.BLACK, null, 0, true);
        assertThrows(IllegalArgumentException.class, () -> waiting.join(alice, t0));

        game.resign(bob);
        assertEquals("1-0", game.getResult());
        assertEquals("resignation", game.getReason());
    }

    private static User user(long id, String name) {
        User user = User.registered(name, "{noop}x");
        ReflectionTestUtils.setField(user, "id", id);   // normally the database assigns ids
        return user;
    }
}
