package com.chessanalyzer.web;

import com.chessanalyzer.stats.StatsService;
import com.chessanalyzer.stats.StatsService.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    /** GET /api/players/edward_nova/stats?timeClass=rapid — your dashboard numbers (timeClass optional). */
    @GetMapping("/api/players/{username}/stats")
    public Stats stats(@PathVariable String username, @RequestParam(required = false) String timeClass) {
        return stats.forPlayer(username, timeClass);
    }
}
