package com.chessanalyzer.puzzle;

import com.chessanalyzer.analysis.Classification;
import com.chessanalyzer.game.Game;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * "You played Nf5?? here. Find the better move." Made from one of your mistakes.
 *
 * Spaced repetition (the idea behind flashcard apps like Anki): you see a puzzle
 * again just before you'd forget it.
 *   solved -> wait 1 day, then 2, 4, 8, ... (max 60): easy ones fade away;
 *   failed -> back to 0 and due again in a few minutes: weak spots keep coming back.
 */
@Entity
@Table(name = "puzzles")
public class Puzzle {

    static final int MAX_INTERVAL_DAYS = 60;
    static final Duration RETRY_AFTER_FAIL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(nullable = false)
    private int ply;

    @Column(nullable = false)
    private String fen;

    @Column(nullable = false)
    private String solutionUci;

    @Column(nullable = false)
    private String solutionSan;

    @Column(nullable = false)
    private String playedUci;

    @Column(nullable = false)
    private String playedSan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Classification classification;

    private Integer evalBeforeCp;
    private Integer evalBeforeMate;

    @Column(nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private Instant dueAt = Instant.now();

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private int solved;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Puzzle() {
    }

    public Puzzle(Game game, int ply, String fen, String solutionUci, String solutionSan,
                  String playedUci, String playedSan, Classification classification,
                  Integer evalBeforeCp, Integer evalBeforeMate) {
        this.game = game;
        this.ply = ply;
        this.fen = fen;
        this.solutionUci = solutionUci;
        this.solutionSan = solutionSan;
        this.playedUci = playedUci;
        this.playedSan = playedSan;
        this.classification = classification;
        this.evalBeforeCp = evalBeforeCp;
        this.evalBeforeMate = evalBeforeMate;
    }

    /** Updates the schedule after an attempt (or "show solution", which counts as failed). */
    public void recordAttempt(boolean correct, Instant now) {
        attempts++;
        if (correct) {
            solved++;
            intervalDays = intervalDays == 0 ? 1 : Math.min(intervalDays * 2, MAX_INTERVAL_DAYS);
            dueAt = now.plus(Duration.ofDays(intervalDays));
        } else {
            intervalDays = 0;
            dueAt = now.plus(RETRY_AFTER_FAIL);
        }
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public int getPly() { return ply; }
    public String getFen() { return fen; }
    public String getSolutionUci() { return solutionUci; }
    public String getSolutionSan() { return solutionSan; }
    public String getPlayedUci() { return playedUci; }
    public String getPlayedSan() { return playedSan; }
    public Classification getClassification() { return classification; }
    public Integer getEvalBeforeCp() { return evalBeforeCp; }
    public Integer getEvalBeforeMate() { return evalBeforeMate; }
    public int getIntervalDays() { return intervalDays; }
    public Instant getDueAt() { return dueAt; }
    public int getAttempts() { return attempts; }
    public int getSolved() { return solved; }
}
