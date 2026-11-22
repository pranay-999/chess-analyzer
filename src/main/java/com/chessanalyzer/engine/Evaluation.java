package com.chessanalyzer.engine;

/**
 * What an engine says about one position. Always from WHITE's point of view
 * (positive = good for white), so values from different moves can be compared.
 *
 * Exactly one of cp / mate is set:
 * @param cp          centipawns: +100 means white is about one pawn better
 * @param mate        moves until mate: +3 = white mates in 3, -2 = black mates in 2,
 *                    0 = the side to move is already checkmated
 * @param bestMoveUci the engine's best move here ("e2e4"), or null if the game is over
 * @param secondCp    score of the SECOND-best move (same conventions), or null if unknown;
 *                    used to spot "Great" moves: the best move was much better than any other
 * @param secondMate  mate score of the second-best move, or null
 */
public record Evaluation(Integer cp, Integer mate, String bestMoveUci, Integer secondCp, Integer secondMate) {

    /** Without a second-best line (our own engine, bots, old data). */
    public Evaluation(Integer cp, Integer mate, String bestMoveUci) {
        this(cp, mate, bestMoveUci, null, null);
    }

    public boolean hasSecond() {
        return secondCp != null || secondMate != null;
    }
}
