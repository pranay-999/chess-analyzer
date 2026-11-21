package com.chessanalyzer.chess;

/** Whether a game is still going, and if not, why it ended (by the rules on the board). */
public enum GameStatus {
    ONGOING,
    CHECKMATE,
    STALEMATE,
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
    INSUFFICIENT_MATERIAL;

    public boolean isOver() {
        return this != ONGOING;
    }

    /** Only checkmate has a winner; every other ending here is a draw. */
    public boolean isDraw() {
        return isOver() && this != CHECKMATE;
    }
}
