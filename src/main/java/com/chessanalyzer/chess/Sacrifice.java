package com.chessanalyzer.chess;

/**
 * "Did this move give material away?"  Used to spot Brilliant moves (good sacrifices).
 *
 * We look two half-moves ahead, captures only:
 *   1. after my move, the opponent tries every capture;
 *   2. after each capture, I may take back on that square (or not), choosing what's best for me;
 *   3. the opponent picks the capture that leaves me worst off.
 * The result is how many pawns' worth of material I lose compared with BEFORE my move.
 *
 *   Bxf7+ Kxf7 (bishop gone, pawn won)          -> 3 - 1 = 2  (a sacrifice)
 *   Nxd5 exd5 (knight for knight, a trade)       -> 0          (not a sacrifice)
 *   a bishop defended by a pawn, offered to a knight: Nxb5 axb5 -> 0 (just a trade)
 *
 * It's deliberately simple (no deeper exchanges); the ENGINE already judged the move
 * good, we only need to know whether it LOOKS like giving material away.
 */
public final class Sacrifice {

    private static final int[] VALUE = {1, 3, 3, 5, 9, 0};   // pawn, knight, bishop, rook, queen, king

    private Sacrifice() {
    }

    /** Pawns' worth of material the mover can lose by playing {@code move} (0 if none). */
    public static int materialGivenUp(Board before, Move move) {
        Color me = before.sideToMove();
        int start = balance(before, me);
        Board afterMine = before.makeMove(move);

        int worstForMe = balance(afterMine, me);   // the opponent may also simply not capture
        for (Move capture : afterMine.legalMoves()) {
            if (!isCapture(afterMine, capture)) continue;
            Board afterCapture = afterMine.makeMove(capture);
            int bestForMe = balance(afterCapture, me);   // I may also not recapture
            for (Move recapture : afterCapture.legalMoves()) {
                // Only taking back ON THAT SQUARE counts (grabbing something elsewhere would
                // just start a new exchange we don't look at).
                if (recapture.to() == capture.to() && isCapture(afterCapture, recapture)) {
                    bestForMe = Math.max(bestForMe, balance(afterCapture.makeMove(recapture), me));
                }
            }
            worstForMe = Math.min(worstForMe, bestForMe);
        }
        return Math.max(0, start - worstForMe);
    }

    /** My material minus the opponent's, in pawns. */
    static int balance(Board board, Color me) {
        int sum = 0;
        for (int sq = 0; sq < 64; sq++) {
            Piece p = board.pieceAt(sq);
            if (p != null) sum += (p.color() == me ? 1 : -1) * VALUE[p.type().ordinal()];
        }
        return sum;
    }

    private static boolean isCapture(Board board, Move m) {
        return board.pieceAt(m.to()) != null
                || (m.to() == board.enPassantSquare() && board.pieceAt(m.from()).type() == PieceType.PAWN);
    }
}
