package com.chessanalyzer.game;

import com.chessanalyzer.chess.Color;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One imported Chess.com game (a row in "games") plus its moves (rows in "moves").
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Many games belong to one player. In the table this is the player_id foreign key;
     * in Java it's a real Player object. LAZY = don't load the player from the DB
     * until someone actually calls getPlayer(). Null for pasted games.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(unique = true)          // null for pasted games
    private String chesscomUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameSource source = GameSource.CHESSCOM;

    @Column(nullable = false, columnDefinition = "text")
    private String pgn;

    @Column(nullable = false)
    private String white;

    @Column(nullable = false)
    private String black;

    private Integer whiteRating;
    private Integer blackRating;

    /** Stored as the text 'WHITE' / 'BLACK'. We reuse the Color enum from our chess package. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Color playerColor;

    @Column(nullable = false)
    private String result;

    private String timeClass;
    private String openingEco;

    @Column(nullable = false)
    private Instant playedAt;

    /**
     * One game has many moves. mappedBy = "game" means the GameMove.game field owns
     * the foreign key. cascade = ALL: saving a Game also saves its moves, deleting it
     * deletes them. So we never need a separate repository for moves.
     */
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ply")
    private List<GameMove> moves = new ArrayList<>();

    protected Game() {
    }

    public Game(Player player, String chesscomUrl, String pgn, String white, String black,
                Integer whiteRating, Integer blackRating, Color playerColor, String result,
                String timeClass, String openingEco, Instant playedAt) {
        this.player = player;
        this.chesscomUrl = chesscomUrl;
        this.pgn = pgn;
        this.white = white;
        this.black = black;
        this.whiteRating = whiteRating;
        this.blackRating = blackRating;
        this.playerColor = playerColor;
        this.result = result;
        this.timeClass = timeClass;
        this.openingEco = openingEco;
        this.playedAt = playedAt;
    }

    /** A game pasted as PGN/FEN: no Chess.com player or URL. A static "factory method" with a clear name. */
    public static Game pasted(String pgn, String white, String black, Integer whiteRating, Integer blackRating,
                              Color playerColor, String result, String openingEco, Instant playedAt) {
        Game game = new Game(null, null, pgn, white, black, whiteRating, blackRating,
                playerColor, result, null, openingEco, playedAt);
        game.source = GameSource.PASTED;
        return game;
    }

    /** A finished game played on this site, stored so it can be analysed. */
    public static Game online(String pgn, String white, String black, String result, Instant playedAt) {
        Game game = new Game(null, null, pgn, white, black, null, null,
                Color.WHITE, result, null, null, playedAt);
        game.source = GameSource.ONLINE;
        return game;
    }

    /** Keeps both sides of the relationship in sync (the list here, and move.game). */
    public void addMove(GameMove move) {
        moves.add(move);
        move.setGame(this);
    }

    public Long getId() { return id; }
    public Player getPlayer() { return player; }
    public String getChesscomUrl() { return chesscomUrl; }
    public GameSource getSource() { return source; }
    public String getPgn() { return pgn; }
    public String getWhite() { return white; }
    public String getBlack() { return black; }
    public Integer getWhiteRating() { return whiteRating; }
    public Integer getBlackRating() { return blackRating; }
    public Color getPlayerColor() { return playerColor; }
    public String getResult() { return result; }
    public String getTimeClass() { return timeClass; }
    public String getOpeningEco() { return openingEco; }
    public Instant getPlayedAt() { return playedAt; }
    public List<GameMove> getMoves() { return moves; }
}
