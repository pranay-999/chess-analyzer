package com.chessanalyzer.web;

import com.chessanalyzer.position.PositionService;
import com.chessanalyzer.position.PositionService.LegalMove;
import com.chessanalyzer.position.PositionService.PositionEval;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for "what if I play this instead?" on the game page.
 * The FEN goes in the query string, URL-encoded by the browser
 * (spaces and slashes become %20 and %2F).
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService positions;

    public PositionController(PositionService positions) {
        this.positions = positions;
    }

    /** GET /api/positions/moves?fen=... — all legal moves (the browser uses this to accept or reject a drag). */
    @GetMapping("/moves")
    public List<LegalMove> legalMoves(@RequestParam String fen) {
        return positions.legalMoves(fen);
    }

    /** GET /api/positions/eval?fen=...&engine=stockfish&depth=14 — engine opinion on one position. */
    @GetMapping("/eval")
    public PositionEval evaluate(@RequestParam String fen,
                                 @RequestParam(defaultValue = "stockfish") String engine,
                                 @RequestParam(defaultValue = "14") int depth) {
        return positions.evaluate(fen, engine, depth);
    }
}
