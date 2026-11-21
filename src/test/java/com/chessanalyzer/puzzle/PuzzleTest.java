package com.chessanalyzer.puzzle;

import com.chessanalyzer.analysis.Classification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The spaced-repetition schedule, with a fixed "now" so the dates are exact. */
class PuzzleTest {

    private final Instant now = Instant.parse("2026-11-22T10:00:00Z");

    private Puzzle puzzle() {
        return new Puzzle(null, 3, "fen", "d2d4", "d4", "g2g4", "g4", Classification.BLUNDER, 0, null);
    }

    @Test
    void solvingDoublesTheWait() {
        Puzzle p = puzzle();
        p.recordAttempt(true, now);
        assertEquals(1, p.getIntervalDays());
        assertEquals(now.plus(Duration.ofDays(1)), p.getDueAt());
        p.recordAttempt(true, now);
        p.recordAttempt(true, now);
        assertEquals(4, p.getIntervalDays());   // 1 -> 2 -> 4
        assertEquals(3, p.getSolved());
    }

    @Test
    void failingBringsItBackSoon() {
        Puzzle p = puzzle();
        p.recordAttempt(true, now);
        p.recordAttempt(true, now);
        p.recordAttempt(false, now);
        assertEquals(0, p.getIntervalDays());
        assertEquals(now.plus(Puzzle.RETRY_AFTER_FAIL), p.getDueAt());
        assertEquals(3, p.getAttempts());
        assertEquals(2, p.getSolved());
    }

    @Test
    void waitIsCapped() {
        Puzzle p = puzzle();
        for (int i = 0; i < 20; i++) p.recordAttempt(true, now);
        assertEquals(Puzzle.MAX_INTERVAL_DAYS, p.getIntervalDays());
    }
}
