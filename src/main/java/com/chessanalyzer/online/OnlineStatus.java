package com.chessanalyzer.online;

/** WAITING (for an opponent) -> ACTIVE -> FINISHED.  A WAITING game can be ABORTED by its creator. */
public enum OnlineStatus {
    WAITING, ACTIVE, FINISHED, ABORTED
}
