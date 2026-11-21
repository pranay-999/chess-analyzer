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
 */
public record Evaluation(Integer cp, Integer mate, String bestMoveUci) {
}
