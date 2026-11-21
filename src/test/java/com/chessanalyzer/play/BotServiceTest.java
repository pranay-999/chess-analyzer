package com.chessanalyzer.play;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.GameState;
import com.chessanalyzer.chess.GameStatus;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.play.BotService.BotReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotServiceTest {

    private final BotService bots = new BotService(new EngineFactory("/usr/games/stockfish", 1, 5, 2_000));

    @Test
    void botPlaysALegalOpeningMoveAsWhite() {
        BotReply reply = bots.reply(List.of(), 2);
        assertNotNull(reply.move());
        assertEquals(GameStatus.ONGOING, reply.status());
        // The move must be legal from the start position.
        assertDoesNotThrow(() -> GameState.replay(Board.startingPosition(), List.of(reply.move().uci())));
    }

    @Test
    void botDeliversMateWhenItCan() {
        // 1. f3 e5 2. g4 — black (the bot) can play Qh4#
        BotReply reply = bots.reply(List.of("f2f3", "e7e5", "g2g4"), 2);
        assertEquals("d8h4", reply.move().uci());
        assertEquals("Qh4#", reply.move().san());
        assertEquals(GameStatus.CHECKMATE, reply.status());
        assertEquals(Color.BLACK, reply.winner());
        assertEquals("0-1", reply.result());
    }

    @Test
    void finishedGameGetsNoBotMove() {
        BotReply reply = bots.reply(List.of("f2f3", "e7e5", "g2g4", "d8h4"), 1);
        assertNull(reply.move());
        assertEquals(GameStatus.CHECKMATE, reply.status());
    }

    @Test
    void rejectsIllegalHistoryAndUnknownLevels() {
        assertThrows(IllegalArgumentException.class, () -> bots.reply(List.of("e2e5"), 1));
        assertThrows(IllegalArgumentException.class, () -> bots.reply(List.of(), 99));
    }

    static boolean stockfishInstalled() {
        return Files.isExecutable(Path.of("/usr/games/stockfish"));
    }

    @Test
    @EnabledIf("stockfishInstalled")
    void stockfishBotReplies() {
        for (BotLevel level : BotLevel.ALL) {
            if (level.stockfishSkill() == null) continue;
            BotReply reply = bots.reply(List.of("e2e4"), level.level());
            assertDoesNotThrow(() -> GameState.replay(Board.startingPosition(), List.of("e2e4", reply.move().uci())),
                    "level " + level.level());
        }
    }
}
