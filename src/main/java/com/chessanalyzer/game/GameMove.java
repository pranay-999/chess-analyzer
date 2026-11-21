package com.chessanalyzer.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One move of a stored game (a row in "moves").
 * Named GameMove so it isn't confused with chess.Move, which is just from/to squares.
 */
@Entity
@Table(name = "moves")
public class GameMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(nullable = false)
    private int ply;

    @Column(nullable = false)
    private String san;

    @Column(nullable = false)
    private String uci;

    @Column(nullable = false)
    private String fenAfter;

    protected GameMove() {
    }

    public GameMove(int ply, String san, String uci, String fenAfter) {
        this.ply = ply;
        this.san = san;
        this.uci = uci;
        this.fenAfter = fenAfter;
    }

    void setGame(Game game) {   // package-private: only Game.addMove calls this
        this.game = game;
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public int getPly() { return ply; }
    public String getSan() { return san; }
    public String getUci() { return uci; }
    public String getFenAfter() { return fenAfter; }
}
