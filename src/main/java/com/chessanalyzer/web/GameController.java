package com.chessanalyzer.web;

import com.chessanalyzer.chess.Color;
import com.chessanalyzer.game.GameDetail;
import com.chessanalyzer.game.GameService;
import com.chessanalyzer.game.GameSummary;
import com.chessanalyzer.game.PageResponse;
import com.chessanalyzer.game.PastedGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;
    private final PastedGameService pastedGames;

    public GameController(GameService gameService, PastedGameService pastedGames) {
        this.gameService = gameService;
        this.pastedGames = pastedGames;
    }

    /** GET /api/players/hikaru/games?page=0&size=20 — newest games first. */
    @GetMapping("/players/{username}/games")
    public PageResponse<GameSummary> listGames(@PathVariable String username,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return gameService.listGames(username, page, size);
    }

    /**
     * POST /api/games/import   body: {"text": "<PGN, FEN or game link>", "playerColor": "WHITE", "asPlayer": "me"}
     * @RequestBody = read the JSON body into a Java record.
     * Answers 201 Created with a Location header pointing at the new game.
     */
    @PostMapping("/games/import")
    public ResponseEntity<GameSummary> importGame(@RequestBody ImportRequest request) {
        GameSummary game = pastedGames.importText(request.text(), request.playerColor(), request.asPlayer());
        return ResponseEntity.created(URI.create("/api/games/" + game.id())).body(game);
    }

    /** @param asPlayer optional: your username, so the side you played is detected automatically */
    public record ImportRequest(String text, Color playerColor, String asPlayer) {
    }

    /** GET /api/games/42 — one game with every move and position. */
    @GetMapping("/games/{id}")
    public GameDetail getGame(@PathVariable long id) {
        return gameService.getGame(id);
    }
}
