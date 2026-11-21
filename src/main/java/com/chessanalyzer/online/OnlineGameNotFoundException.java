package com.chessanalyzer.online;

public class OnlineGameNotFoundException extends RuntimeException {

    public OnlineGameNotFoundException(String publicId) {
        super("Online game not found: " + publicId);
    }
}
