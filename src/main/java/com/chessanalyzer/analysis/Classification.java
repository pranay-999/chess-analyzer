package com.chessanalyzer.analysis;

/**
 * How good a played move was. Most labels come from how much the move lowered the
 * mover's chance of winning; the two "!" labels need a bit more (see MoveAnalyzer.classify).
 */
public enum Classification {
    BRILLIANT,   // !!  a good SACRIFICE: gave up material and it was (about) the best move
    GREAT,       // !   the ONLY good move: best, and every other move was much worse
    BEST,        // the engine's own first choice
    GOOD,        // lost less than 10% winning chance
    INACCURACY,  // lost 10-20%
    MISTAKE,     // lost 20-30%
    BLUNDER      // lost 30% or more
}
