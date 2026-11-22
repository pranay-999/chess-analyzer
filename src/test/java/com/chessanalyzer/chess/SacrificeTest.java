package com.chessanalyzer.chess;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SacrificeTest {

    @ParameterizedTest(name = "{2}: {1}")
    @CsvSource(delimiter = '|', value = {
            // Bishop takes f7 with check; the king takes it back: a bishop for a pawn
            "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4 | c4f7 | 2 | classic Bxf7+ sacrifice",
            // Knight to a square a pawn attacks, nothing gained: a knight for nothing
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2 | g1f3 | 0 | quiet developing move",
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2 | f8a3 | 3 | bishop hangs to the b-pawn",
            // Trade: knight takes a knight, pawn recaptures -> even
            "r1bqkbnr/pppp1ppp/2n5/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq d3 0 3 | c6d4 | 0 | Nxd4 Nxd4 is a fair trade",
            // The knight on c6 attacks a5: pushing the pawn there gives it away
            "4k3/8/2n5/8/P7/8/8/4K3 w - - 0 1 | a4a5 | 1 | pawn pushed into the knight's reach",
            "4k3/8/2n5/8/P7/8/8/4K3 w - - 0 1 | e1e2 | 0 | king move, nothing hangs",
            // Bishop defended by a pawn, attacked by a knight: Nxb5 axb5 is just a trade
            "4k3/2n5/8/8/P7/3B4/8/4K3 w - - 0 1 | d3b5 | 0 | defended piece is not a sacrifice",
            "4k3/2n5/8/8/8/3B4/8/4K3 w - - 0 1 | d3b5 | 3 | same move without the defender: a sacrifice",
    })
    void measuresMaterialGivenUp(String fen, String uci, int expected, String description) {
        assertEquals(expected, Sacrifice.materialGivenUp(Board.fromFen(fen), Move.fromUci(uci)), description);
    }
}
