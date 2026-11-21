package com.chessanalyzer.chess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Perft" (performance test) = count every possible move sequence to a fixed depth.
 * The correct counts for these positions are published (chessprogramming.org/Perft_Results)
 * and every engine uses them. If even one rule is wrong (castling through check,
 * en passant pins, promotions...) the numbers stop matching.
 */
class PerftTest {

    static long perft(Board board, int depth) {
        var moves = board.legalMoves();
        if (depth == 1) {
            return moves.size();
        }
        long count = 0;
        for (Move move : moves) {
            count += perft(board.makeMove(move), depth - 1);
        }
        return count;
    }

    // @ParameterizedTest runs the same test once per line below: fen | depth | expected count
    @ParameterizedTest(name = "depth {1} = {2} : {0}")
    @CsvSource(delimiter = '|', value = {
            // Standard starting position
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 1 | 20",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 2 | 400",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 3 | 8902",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 4 | 197281",
            // "Kiwipete": castling, pins, en passant, promotions all at once
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 1 | 48",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 2 | 2039",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 3 | 97862",
            // Endgame with tricky en-passant discovered checks
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 4 | 43238",
            // Promotions and checks
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 3 | 9467",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8 | 3 | 62379",
    })
    void moveCountsMatchKnownValues(String fen, int depth, long expected) {
        assertEquals(expected, perft(Board.fromFen(fen), depth));
    }
}
