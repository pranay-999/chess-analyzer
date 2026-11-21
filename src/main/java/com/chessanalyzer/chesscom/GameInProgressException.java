package com.chessanalyzer.chesscom;

/**
 * Someone tried to import a game that is still being played. We refuse on purpose:
 * using an engine during a live game is cheating under Chess.com's and Lichess's
 * fair-play rules (and gets accounts closed). Extends IllegalArgumentException,
 * so ApiExceptionHandler answers 400 with this message.
 */
public class GameInProgressException extends IllegalArgumentException {

    public GameInProgressException() {
        super("This game is still being played. Engine analysis is only allowed after the game "
                + "has finished (fair-play rules). Come back when it's over!");
    }
}
