package com.chessanalyzer.web;

import com.chessanalyzer.online.LobbyEntry;
import com.chessanalyzer.online.OnlineGameService;
import com.chessanalyzer.online.OnlineGameView;
import com.chessanalyzer.user.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Online play. Everything under /api/online requires a login (or guest), see SecurityConfig.
 * @AuthenticationPrincipal gives us the logged-in user that AuthController stored in the session.
 */
@RestController
@RequestMapping("/api/online/games")
public class OnlineGameController {

    private final OnlineGameService online;

    public OnlineGameController(OnlineGameService online) {
        this.online = online;
    }

    public record CreateRequest(Integer minutes, int increment, String color, boolean listed) {
    }

    public record MoveRequest(String uci) {
    }

    /** POST /api/online/games  {"minutes": 5, "increment": 0, "color": "RANDOM", "listed": true} */
    @PostMapping
    public ResponseEntity<OnlineGameView> create(@AuthenticationPrincipal UserPrincipal me, @RequestBody CreateRequest req) {
        OnlineGameView game = online.create(me, req.minutes(), req.increment(), req.color(), req.listed());
        return ResponseEntity.created(URI.create("/api/online/games/" + game.id())).body(game);
    }

    /** GET /api/online/games/open — the lobby. */
    @GetMapping("/open")
    public List<LobbyEntry> open() {
        return online.openGames();
    }

    /** GET /api/online/games/mine — your waiting and running games. */
    @GetMapping("/mine")
    public List<OnlineGameView> mine(@AuthenticationPrincipal UserPrincipal me) {
        return online.myGames(me);
    }

    @GetMapping("/{id}")
    public OnlineGameView get(@PathVariable String id) {
        return online.get(id);
    }

    @PostMapping("/{id}/join")
    public OnlineGameView join(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        return online.join(me, id);
    }

    /** POST /api/online/games/{id}/moves  {"uci": "e2e4"} */
    @PostMapping("/{id}/moves")
    public OnlineGameView move(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id,
                               @RequestBody MoveRequest req) {
        return online.move(me, id, req.uci());
    }

    @PostMapping("/{id}/resign")
    public OnlineGameView resign(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        return online.resign(me, id);
    }

    @PostMapping("/{id}/cancel")
    public OnlineGameView cancel(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        return online.cancel(me, id);
    }
}
