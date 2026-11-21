package com.chessanalyzer.analysis;

/** How good a played move was, judged by how much it lowered the mover's chance of winning. */
public enum Classification {
    BEST,        // the engine's own first choice
    GOOD,        // lost less than 10% winning chance
    INACCURACY,  // lost 10-20%
    MISTAKE,     // lost 20-30%
    BLUNDER      // lost 30% or more
}
