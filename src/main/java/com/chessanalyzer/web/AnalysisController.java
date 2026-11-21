package com.chessanalyzer.web;

import com.chessanalyzer.analysis.AnalysisService;
import com.chessanalyzer.analysis.AnalysisView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * POST /api/games/42/analysis?engine=stockfish&depth=16
     * POST /api/games/42/analysis?engine=my-engine&depth=5
     * Answers 202 Accepted ("I've started, not finished") instead of 200 OK.
     * ResponseEntity lets us choose the status code ourselves.
     */
    @PostMapping
    public ResponseEntity<AnalysisView> start(@PathVariable long gameId,
                                              @RequestParam(defaultValue = "stockfish") String engine,
                                              @RequestParam(defaultValue = "16") int depth) {
        return ResponseEntity.accepted().body(analysisService.start(gameId, engine, depth));
    }

    /** GET /api/games/42/analysis?engine=my-engine — newest analysis by that engine. */
    @GetMapping
    public AnalysisView latest(@PathVariable long gameId,
                               @RequestParam(defaultValue = "stockfish") String engine) {
        return analysisService.latest(gameId, engine);
    }
}
