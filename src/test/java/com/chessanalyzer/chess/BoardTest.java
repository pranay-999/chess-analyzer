package com.chessanalyzer.chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            Board.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    })
    void fenRoundTrip(String fen) {
        assertEquals(fen, Board.fromFen(fen).toFen());
    }

    @Test
    void rejectsBrokenFen() {
        assertThrows(IllegalArgumentException.class, () -> Board.fromFen("not a fen"));
        assertThrows(IllegalArgumentException.class,
                () -> Board.fromFen("rnbqkbnr/ppppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        // no black king / two white kings
        assertThrows(IllegalArgumentException.class, () -> Board.fromFen("8/8/8/8/8/8/8/K7 w - - 0 1"));
        assertThrows(IllegalArgumentException.class, () -> Board.fromFen("k7/8/8/8/8/8/8/KK6 w - - 0 1"));
    }

    @Test
    void doublePawnPushSetsEnPassantSquare() {
        Board after = Board.startingPosition().makeMove(Move.fromUci("e2e4"));
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", after.toFen());
    }

    @Test
    void enPassantRemovesCapturedPawn() {
        Board board = Board.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        Board after = board.makeMove(Move.fromUci("e5d6"));
        assertNull(after.pieceAt(Square.parse("d5")));
        assertEquals(Piece.WHITE_PAWN, after.pieceAt(Square.parse("d6")));
    }

    @Test
    void castlingMovesRookAndClearsRights() {
        Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Board after = board.makeMove(Move.fromUci("e1g1"));
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", after.toFen());
    }

    @Test
    void detectsCheckmateAndStalemate() {
        // Fool's mate
        Board mated = Board.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        assertTrue(mated.isCheckmate());

        // Black king in the corner, no moves, not in check
        Board stalemate = Board.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertTrue(stalemate.isStalemate());
        assertFalse(stalemate.isCheckmate());
    }
}
