package com.chessanalyzer.web;

import com.chessanalyzer.game.GameImportService;
import com.chessanalyzer.game.SyncResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP layer only: read the request, call the service, return the result.
 * No business logic here — that lives in GameImportService.
 */
@RestController
@RequestMapping("/api/players")   // every path in this class starts with /api/players
public class PlayerController {

    private final GameImportService importService;

    public PlayerController(GameImportService importService) {
        this.importService = importService;
    }

    /**
     * POST /api/players/{username}/sync            -> import everything new
     * POST /api/players/{username}/sync?months=3   -> only the last 3 months
     *
     * POST (not GET) because this changes data on the server.
     */
    @PostMapping("/{username}/sync")
    public SyncResult sync(@PathVariable String username,
                           @RequestParam(required = false) Integer months) {
        return importService.sync(username, months);
    }
}
