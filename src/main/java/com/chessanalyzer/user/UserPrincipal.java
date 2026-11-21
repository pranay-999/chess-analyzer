package com.chessanalyzer.user;

import java.io.Serializable;

/**
 * "Who is logged in", as Spring Security keeps it for the rest of the session.
 * It's stored inside the HTTP session, which is why it must be Serializable.
 * Controllers get it with @AuthenticationPrincipal UserPrincipal me.
 */
public record UserPrincipal(long id, String username, boolean guest) implements Serializable {

    public static UserPrincipal of(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.isGuest());
    }
}
