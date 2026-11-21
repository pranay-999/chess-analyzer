package com.chessanalyzer.chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameStateTest {

    @Test
    void foolsMateIsCheckmateForBlack() {
        GameState game = GameState.replay(Board.startingPosition(), List.of("f2f3", "e7e5", "g2g4", "d8h4"));
        assertEquals(GameStatus.CHECKMATE, game.status());
        assertEquals(Color.BLACK, game.winner());
        assertEquals("0-1", game.result());
    }

    @Test
    void ongoingGameHasNoResult() {
        GameState game = GameState.replay(Board.startingPosition(), List.of("e2e4"));
        assertEquals(GameStatus.ONGOING, game.status());
        assertNull(game.winner());
        assertEquals("*", game.result());
        assertEquals(1, game.plyCount());
    }

    @Test
    void illegalMovesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GameState.replay(Board.startingPosition(), List.of("e2e5")));
        GameState mated = GameState.replay(Board.startingPosition(), List.of("f2f3", "e7e5", "g2g4", "d8h4"));
        assertThrows(IllegalArgumentException.class, () -> mated.play(Move.fromUci("a2a3")));
    }

    @Test
    void stalemate() {
        GameState game = GameState.replay(Board.fromFen("7k/8/6K1/8/8/8/8/5Q2 w - - 0 1"), List.of("f1f7"));
        assertEquals(GameStatus.STALEMATE, game.status());
        assertEquals("1/2-1/2", game.result());
    }

    @Test
    void threefoldRepetitionByShufflingKnights() {
        List<String> moves = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            moves.addAll(List.of("g1f3", "g8f6", "f3g1", "f6g8"));   // back to the start position each time
        }
        GameState game = GameState.replay(Board.startingPosition(), moves);
        assertEquals(GameStatus.THREEFOLD_REPETITION, game.status());   // start + 2 returns = 3 times
        assertEquals("1/2-1/2", game.result());
    }

    @Test
    void fiftyMoveRule() {
        // Halfmove clock at 99: one more quiet move reaches 100.
        GameState game = GameState.replay(Board.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 99 80"), List.of("a1a2"));
        assertEquals(GameStatus.FIFTY_MOVE_RULE, game.status());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1, true",       // K vs K
            "4k3/8/8/8/8/8/8/2B1K3 w - - 0 1, true",     // K+B vs K
            "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1, true",     // K+N vs K
            "2b1k3/8/8/8/8/8/8/3BK3 w - - 0 1, true",    // bishops on same colour (c8 and d1 are both light)
            "3bk3/8/8/8/8/8/8/3BK3 w - - 0 1, false",    // bishops on opposite colours
            "4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1, false",   // two knights: mate possible (with help)
            "4k3/8/8/8/8/8/P7/4K3 w - - 0 1, false",     // a pawn can promote
    })
    void insufficientMaterial(String fen, boolean expected) {
        assertEquals(expected, GameState.isInsufficientMaterial(Board.fromFen(fen)));
    }
}
