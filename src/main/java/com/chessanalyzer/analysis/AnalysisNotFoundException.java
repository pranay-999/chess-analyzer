package com.chessanalyzer.analysis;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(long gameId, String engine) {
        super("Game " + gameId + " has not been analysed with " + engine + " yet");
    }
}
