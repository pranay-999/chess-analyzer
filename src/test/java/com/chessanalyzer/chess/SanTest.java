package com.chessanalyzer.chess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanTest {

    // fen | move in UCI | expected SAN
    @ParameterizedTest(name = "{1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | e2e4 | e4",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | g1f3 | Nf3",
            // two knights can reach d2 -> add the file
            "4k3/8/8/8/8/8/8/1N1K1N2 w - - 0 1 | b1d2 | Nbd2",
            // two rooks on the a-file can reach a3 -> add the rank
            "4k3/8/8/R7/8/8/8/R3K3 w - - 0 1 | a1a3 | R1a3",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | e1g1 | O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1 | e1c1 | O-O-O",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1 | e5d6 | exd6",
            "8/P7/8/8/8/8/8/k6K w - - 0 1 | a7a8q | a8=Q+",
            "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2 | d8h4 | Qh4#",
    })
    void writesAndReadsSan(String fen, String uci, String san) {
        Board board = Board.fromFen(fen);
        Move move = Move.fromUci(uci);
        assertEquals(san, San.toSan(board, move));
        assertEquals(move, San.fromSan(board, san));
    }

    @ParameterizedTest
    @CsvSource({"0-0, e1g1", "O-O, e1g1", "0-0-0, e1c1"})
    void acceptsZeroCastlingNotation(String san, String uci) {
        Board board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertEquals(Move.fromUci(uci), San.fromSan(board, san));
    }
}
