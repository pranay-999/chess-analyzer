package com.chessanalyzer.chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningBookTest {

    private static Board play(String... uci) {
        Board board = Board.startingPosition();
        for (String m : uci) board = board.makeMove(Move.fromUci(m));
        return board;
    }

    @Test
    void knowsTheItalianGame() {
        Board italian = play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4");
        assertTrue(OpeningBook.contains(italian.toFen()));
        assertEquals("Italian Game", OpeningBook.nameOf(italian.toFen()).orElseThrow());
    }

    @Test
    void transpositionsCountToo() {
        // Queen's Gambit Declined reached in a different order: still theory.
        Board qgd = play("c2c4", "e7e6", "d2d4", "d7d5");
        assertTrue(OpeningBook.contains(qgd.toFen()));
    }

    @Test
    void nonsenseIsNotTheory() {
        Board silly = play("a2a3", "h7h6", "h2h4", "a7a6", "a1a2", "h8h7");
        assertFalse(OpeningBook.contains(silly.toFen()));
    }

    @Test
    void moveCountersDontMatter() {
        String fen = play("e2e4").toFen();                    // "... b KQkq e3 0 1"
        assertTrue(OpeningBook.contains(fen.replace(" 0 1", " 7 30")));
    }
}
