package com.chessanalyzer.online;

import com.chessanalyzer.chess.Color;

/**
 * One open game in the lobby.
 * @param joinerColor the side YOU get if you join
 */
public record LobbyEntry(String id, String creator, boolean creatorGuest, Long initialMs, long incrementMs,
                         Color joinerColor) {

    static LobbyEntry of(OnlineGame game) {
        return new LobbyEntry(game.getPublicId(), game.getCreator().getUsername(), game.getCreator().isGuest(),
                game.getInitialMs(), game.getIncrementMs(), game.getWhite() == null ? Color.WHITE : Color.BLACK);
    }
}
