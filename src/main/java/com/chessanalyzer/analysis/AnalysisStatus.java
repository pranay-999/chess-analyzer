package com.chessanalyzer.analysis;

/** Life cycle of an analysis job: PENDING -> RUNNING -> DONE (or FAILED). */
public enum AnalysisStatus {
    PENDING, RUNNING, DONE, FAILED
}
