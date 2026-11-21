package com.chessanalyzer.play;

import java.util.List;

/**
 * The bots you can play. Weak bots use OUR engine with a tiny search depth; stronger
 * ones use Stockfish with its "Skill Level" turned down and a limited depth.
 *
 * @param stockfishSkill null = use our own engine
 * @param depth          search depth (half-moves)
 * @param approxElo      rough playing strength, just to help people choose
 */
public record BotLevel(int level, String name, String description, Integer stockfishSkill, int depth, int approxElo) {

    public static final List<BotLevel> ALL = List.of(
            new BotLevel(1, "Pawnie", "My own engine, looking 1 move ahead. Grabs material, misses tactics.", null, 1, 600),
            new BotLevel(2, "Rookie", "My own engine, looking 2 moves ahead.", null, 2, 900),
            new BotLevel(3, "Casual", "Stockfish on its lowest skill: makes human-like mistakes.", 0, 5, 1200),
            new BotLevel(4, "Club player", "Stockfish skill 5.", 5, 8, 1500),
            new BotLevel(5, "Strong", "Stockfish skill 10.", 10, 10, 1800),
            new BotLevel(6, "Expert", "Stockfish skill 15.", 15, 12, 2100),
            new BotLevel(7, "Master", "Full-strength Stockfish (shallow search). Good luck.", 20, 14, 2600));

    public static BotLevel of(int level) {
        return ALL.stream().filter(b -> b.level == level).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bot level " + level + " (choose 1-" + ALL.size() + ")"));
    }
}
