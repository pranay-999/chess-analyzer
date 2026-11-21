package com.chessanalyzer.web;

import com.chessanalyzer.play.BotLevel;
import com.chessanalyzer.play.BotService;
import com.chessanalyzer.play.BotService.BotReply;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotService bots;

    public BotController(BotService bots) {
        this.bots = bots;
    }

    /** GET /api/bots — the bots to choose from. */
    @GetMapping
    public List<BotLevel> levels() {
        return BotLevel.ALL;
    }

    /**
     * POST /api/bots/move   body: {"level": 3, "moves": ["e2e4", "e7e5", "g1f3"]}
     * Returns the bot's reply (or just the final status if your move ended the game).
     */
    @PostMapping("/move")
    public BotReply move(@RequestBody BotMoveRequest request) {
        return bots.reply(request.moves(), request.level());
    }

    public record BotMoveRequest(int level, List<String> moves) {
    }
}
