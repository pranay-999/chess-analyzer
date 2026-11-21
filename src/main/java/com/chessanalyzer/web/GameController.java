package com.chessanalyzer.web;

import com.chessanalyzer.game.GameDetail;
import com.chessanalyzer.game.GameService;
import com.chessanalyzer.game.GameSummary;
import com.chessanalyzer.game.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** GET /api/players/hikaru/games?page=0&size=20 — newest games first. */
    @GetMapping("/players/{username}/games")
    public PageResponse<GameSummary> listGames(@PathVariable String username,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return gameService.listGames(username, page, size);
    }

    /** GET /api/games/42 — one game with every move and position. */
    @GetMapping("/games/{id}")
    public GameDetail getGame(@PathVariable long id) {
        return gameService.getGame(id);
    }
}
