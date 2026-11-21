package com.chessanalyzer.game;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(long id) {
        super("Game not found: " + id);
    }
}
