package com.chessanalyzer.game;

/** Where a stored game came from. */
public enum GameSource {
    CHESSCOM,   // imported with "Sync from Chess.com"
    PASTED      // a PGN or FEN pasted into the analysis board
}
