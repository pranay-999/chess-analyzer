package com.chessanalyzer.chess;

/**
 * One move of a replayed game, in the shape we will store in the "moves" table.
 *
 * @param ply      1 = first move of the game, 2 = reply, ...
 * @param san      human notation ("Nf3")
 * @param uci      engine notation ("g1f3")
 * @param fenAfter the position after this move
 */
public record PlayedMove(int ply, String san, String uci, String fenAfter) {
}
