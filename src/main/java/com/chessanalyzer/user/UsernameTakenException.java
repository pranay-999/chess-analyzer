package com.chessanalyzer.user;

/** Sign-up with a name that already exists. ApiExceptionHandler answers 409 Conflict. */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("The username '" + username + "' is already taken");
    }
}
