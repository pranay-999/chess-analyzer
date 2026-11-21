package com.chessanalyzer.chesscom;

/**
 * Thrown when Chess.com has no such user.
 * web/ApiExceptionHandler turns it into an HTTP 404 response.
 */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(String username) {
        super("Chess.com player not found: " + username);
    }
}
