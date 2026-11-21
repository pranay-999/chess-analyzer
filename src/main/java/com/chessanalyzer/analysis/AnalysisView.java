package com.chessanalyzer.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * JSON for an analysis. While it runs, the browser polls this and shows
 * analyzedPlies / totalPlies; when status is DONE, {@code moves} holds the verdicts.
 */
public record AnalysisView(long id, long gameId, String engine, int depth, AnalysisStatus status,
                           int analyzedPlies, int totalPlies, BigDecimal accuracyWhite,
                           BigDecimal accuracyBlack, String error, List<MoveEvalView> moves) {

    public record MoveEvalView(int ply, Integer evalCp, Integer mateIn, String bestMoveUci,
                               String bestMoveSan, Integer cpLoss, Classification classification) {
    }

    /** Only touches the lazy evaluations list when the analysis is DONE. */
    static AnalysisView from(Analysis a, long gameId) {
        List<MoveEvalView> moves = a.getStatus() != AnalysisStatus.DONE ? List.of()
                : a.getEvaluations().stream()
                .map(e -> new MoveEvalView(e.getPly(), e.getEvalCp(), e.getMateIn(), e.getBestMoveUci(),
                        e.getBestMoveSan(), e.getCpLoss(), e.getClassification()))
                .toList();
        return new AnalysisView(a.getId(), gameId, a.getEngine(), a.getDepth(), a.getStatus(),
                a.getAnalyzedPlies(), a.getTotalPlies(), a.getAccuracyWhite(), a.getAccuracyBlack(),
                a.getError(), moves);
    }
}
