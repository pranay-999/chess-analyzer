package com.chessanalyzer.online;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.San;
import com.chessanalyzer.position.PositionService.LegalMove;
import com.chessanalyzer.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON sent to the browser: by REST responses AND pushed over the WebSocket to
 * everyone watching. Both players get the same message, so it doesn't say "you";
 * the browser compares the names with its own logged-in user.
 *
 * whiteMs / blackMs = time left at the moment this view was made. The browser counts
 * down locally from when it RECEIVED the view, which avoids trusting the computer clocks to agree.
 */
public record OnlineGameView(String id, OnlineStatus status, PlayerView white, PlayerView black, String creator,
                             Long initialMs, long incrementMs, Long whiteMs, Long blackMs, boolean clockRunning,
                             Color sideToMove, String fen, List<LegalMove> moves,
                             String result, String reason, Long analysisGameId) {

    public record PlayerView(String username, boolean guest) {
        static PlayerView of(User user) {
            return user == null ? null : new PlayerView(user.getUsername(), user.isGuest());
        }
    }

    public static OnlineGameView of(OnlineGame game, Instant now) {
        // Replay the moves to get SAN and the position after each one.
        List<LegalMove> moves = new ArrayList<>();
        Board board = Board.startingPosition();
        for (String uci : game.uciMoves()) {
            Move move = Move.fromUci(uci);
            String san = San.toSan(board, move);
            board = board.makeMove(move);
            moves.add(new LegalMove(uci, san, board.toFen()));
        }
        boolean timed = game.hasClock() && game.getStatus() != OnlineStatus.WAITING;
        return new OnlineGameView(game.getPublicId(), game.getStatus(),
                PlayerView.of(game.getWhite()), PlayerView.of(game.getBlack()), game.getCreator().getUsername(),
                game.getInitialMs(), game.getIncrementMs(),
                timed ? Math.max(0, game.remaining(Color.WHITE, now)) : game.getInitialMs(),
                timed ? Math.max(0, game.remaining(Color.BLACK, now)) : game.getInitialMs(),
                timed && game.getStatus() == OnlineStatus.ACTIVE,
                board.sideToMove(), board.toFen(), moves,
                game.getResult(), game.getReason(), game.getGameId());
    }
}
