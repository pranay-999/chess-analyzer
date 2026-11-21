package com.chessanalyzer.play;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.GameState;
import com.chessanalyzer.chess.GameStatus;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.San;
import com.chessanalyzer.engine.Engine;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.position.PositionService.LegalMove;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Playing against a bot. The browser keeps the game (and the clocks); every time
 * it's the bot's turn it sends ALL moves so far. We:
 *  1. replay them with GameState: this checks every move is legal (the server
 *     never trusts the browser) and detects a finished game;
 *  2. if the game is still on, ask the bot's engine for a move;
 *  3. answer with that move and the new game status.
 *
 * Stateless: nothing is stored until the player chooses "Analyse this game".
 */
@Service
public class BotService {

    private final EngineFactory engines;

    public BotService(EngineFactory engines) {
        this.engines = engines;
    }

    public record BotReply(LegalMove move, GameStatus status, String result, Color winner) {
    }

    public BotReply reply(List<String> uciMoves, int level) {
        BotLevel bot = BotLevel.of(level);
        GameState game = GameState.replay(Board.startingPosition(), uciMoves == null ? List.of() : uciMoves);
        if (game.status().isOver()) {
            return new BotReply(null, game.status(), game.result(), game.winner());   // the human's move ended it
        }

        Board board = game.board();
        String uci;
        try (Engine engine = bot.stockfishSkill() == null
                ? engines.openMyEngine(bot.depth())
                : engines.openStockfish(bot.stockfishSkill())) {
            uci = engine.evaluate(board.toFen(), bot.depth()).bestMoveUci();
        }
        Move move = Move.fromUci(uci);
        String san = San.toSan(board, move);
        game = game.play(move);
        return new BotReply(new LegalMove(uci, san, game.board().toFen()), game.status(), game.result(), game.winner());
    }
}
