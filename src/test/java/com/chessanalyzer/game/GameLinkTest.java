package com.chessanalyzer.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLinkTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "https://www.chess.com/game/live/187517802954 | chesscom | live | 187517802954",
            "https://www.chess.com/game/daily/12345?username=me | chesscom | daily | 12345",
            "https://www.chess.com/live/game/999 | chesscom | live | 999",
            "https://www.chess.com/analysis/game/live/777?tab=review | chesscom | live | 777",
            "Check out my game! https://www.chess.com/game/live/42 #chess | chesscom | live | 42",
            "https://lichess.org/HqjDTU4A | lichess | | HqjDTU4A",
            "https://lichess.org/HqjDTU4Aabcd | lichess | | HqjDTU4A",
            "https://lichess.org/HqjDTU4A/black#12 | lichess | | HqjDTU4A",
    })
    void findsLinks(String text, String site, String kind, String id) {
        GameLink link = GameLink.find(text).orElseThrow();
        assertEquals(site, link.site());
        assertEquals(kind, link.kind());
        assertEquals(id, link.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1. e4 e5 2. Nf3",
            "https://lichess.org/analysis",
            "https://lichess.org/training/12345",
            "https://www.chess.com/member/hikaru",
    })
    void ignoresNonGameText(String text) {
        assertTrue(GameLink.find(text).isEmpty());
    }

    @Test
    void nullIsFine() {
        assertTrue(GameLink.find(null).isEmpty());
    }
}
