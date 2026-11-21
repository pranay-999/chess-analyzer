package com.chessanalyzer.game;

import com.chessanalyzer.chess.Color;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSummaryTest {

    @ParameterizedTest
    @CsvSource({
            "1-0, WHITE, WIN",
            "1-0, BLACK, LOSS",
            "0-1, BLACK, WIN",
            "0-1, WHITE, LOSS",
            "1/2-1/2, WHITE, DRAW",
            "*, BLACK, UNKNOWN",
    })
    void outcomeIsFromPlayersPointOfView(String result, Color color, String expected) {
        assertEquals(expected, GameSummary.outcome(result, color));
    }
}
