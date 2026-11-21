package com.chessanalyzer.web;

import com.chessanalyzer.puzzle.PuzzleService;
import com.chessanalyzer.puzzle.PuzzleService.AttemptResult;
import com.chessanalyzer.puzzle.PuzzleService.NextPuzzle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService puzzles;

    public PuzzleController(PuzzleService puzzles) {
        this.puzzles = puzzles;
    }

    public record AttemptRequest(String uci) {
    }

    /** GET /api/puzzles/next?player=edward_nova — the next puzzle that's due (+ counts). Omit player for all games. */
    @GetMapping("/next")
    public NextPuzzle next(@RequestParam(required = false) String player) {
        return puzzles.next(player);
    }

    /** POST /api/puzzles/7/attempt {"uci": "d2d4"} — right or wrong, plus the solution. */
    @PostMapping("/{id}/attempt")
    public AttemptResult attempt(@PathVariable long id, @RequestBody AttemptRequest body) {
        return puzzles.attempt(id, body.uci());
    }

    /** POST /api/puzzles/7/reveal — give up and see the answer (it comes back soon). */
    @PostMapping("/{id}/reveal")
    public AttemptResult reveal(@PathVariable long id) {
        return puzzles.reveal(id);
    }
}
