package com.chessanalyzer.online;

import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.GameState;
import com.chessanalyzer.chess.GameStatus;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.user.User;
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
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A live game between two people. The SERVER is the referee: it holds the moves and
 * the clocks, and every rule is checked here. A player's browser can only ASK to
 * move. That's what makes cheating by editing JavaScript impossible.
 *
 * The rules live in this entity's methods (join, play, resign, ...), not in the
 * service. This is called a "rich domain model": the object protects its own
 * consistency, and the service only loads, calls and saves.
 *
 * Clocks: we store each side's remaining time at the moment the current turn began
 * (turnStartedAt). Time left NOW = stored - (now - turnStartedAt) for the side to move.
 * So we only write to the database when something happens, never every second.
 */
@Entity
@Table(name = "online_games")
public class OnlineGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_id")
    private User white;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_id")
    private User black;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnlineStatus status;

    @Column(nullable = false)
    private boolean listed;

    private Long initialMs;          // null = untimed
    @Column(nullable = false)
    private long incrementMs;
    private Long whiteMs;
    private Long blackMs;
    private Instant turnStartedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String moves = "";

    private String result;
    private String reason;
    private Long gameId;             // the stored copy in "games", once finished

    /**
     * Optimistic locking. Hibernate adds "WHERE version = ?" to every UPDATE and bumps
     * the number. If two requests load version 7 and both try to save, the second
     * UPDATE matches no row, and Hibernate throws instead of silently overwriting the
     * first one (e.g. a move and a resignation arriving at the same millisecond).
     */
    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected OnlineGame() {
    }

    public static OnlineGame create(String publicId, User creator, Color creatorColor,
                                    Long initialMs, long incrementMs, boolean listed) {
        OnlineGame game = new OnlineGame();
        game.publicId = publicId;
        game.creator = creator;
        if (creatorColor == Color.WHITE) game.white = creator; else game.black = creator;
        game.status = OnlineStatus.WAITING;
        game.initialMs = initialMs;
        game.incrementMs = incrementMs;
        game.listed = listed;
        return game;
    }

    // ------------------------------------------------------------------ actions

    public void join(User opponent, Instant now) {
        require(status == OnlineStatus.WAITING, "This game has already started or ended");
        require(!isPlayer(opponent), "You can't play against yourself. Send the link to a friend");
        if (white == null) white = opponent; else black = opponent;
        status = OnlineStatus.ACTIVE;
        if (hasClock()) {
            whiteMs = initialMs;
            blackMs = initialMs;
        }
        turnStartedAt = now;
    }

    public void play(User player, String uci, Instant now) {
        require(status == OnlineStatus.ACTIVE, "The game is not in progress");
        Color color = colorOf(player);
        require(color != null, "You are not playing in this game");
        GameState state = state();
        require(state.board().sideToMove() == color, "It's not your turn");

        if (hasClock()) {
            long left = remaining(color, now);
            if (left <= 0) {               // they moved, but too late
                flag(color);
                return;
            }
            setStoredTime(color, left + incrementMs);
        }
        GameState next = state.play(Move.fromUci(uci));   // throws IllegalArgumentException if illegal
        moves = moves.isEmpty() ? uci : moves + " " + uci;
        turnStartedAt = now;
        if (next.status().isOver()) {
            finish(next.result(), describe(next.status()));
        }
    }

    public void resign(User player) {
        require(status == OnlineStatus.ACTIVE, "The game is not in progress");
        Color color = colorOf(player);
        require(color != null, "You are not playing in this game");
        finish(color == Color.WHITE ? "0-1" : "1-0", "resignation");
    }

    public void cancel(User player) {
        require(status == OnlineStatus.WAITING, "Only a game that hasn't started can be cancelled");
        require(creator.getId().equals(player.getId()), "Only the creator can cancel this game");
        status = OnlineStatus.ABORTED;
    }

    /** Called regularly by ClockChecker: ends the game if the side to move has no time left. */
    public boolean flagIfTimeUp(Instant now) {
        if (status != OnlineStatus.ACTIVE || !hasClock()) return false;
        Color toMove = state().board().sideToMove();
        if (remaining(toMove, now) > 0) return false;
        flag(toMove);
        return true;
    }

    public void attachStoredGame(long storedGameId) {
        this.gameId = storedGameId;
    }

    // ------------------------------------------------------------------ queries

    public GameState state() {
        return GameState.replay(Board.startingPosition(), uciMoves());
    }

    public List<String> uciMoves() {
        return moves.isEmpty() ? List.of() : Arrays.asList(moves.split(" "));
    }

    /** Time left for {@code color} at {@code now}; only the side to move is ticking. */
    public long remaining(Color color, Instant now) {
        long stored = color == Color.WHITE ? whiteMs : blackMs;
        boolean ticking = status == OnlineStatus.ACTIVE && state().board().sideToMove() == color;
        return ticking ? stored - Duration.between(turnStartedAt, now).toMillis() : stored;
    }

    public Color colorOf(User user) {
        if (white != null && white.getId().equals(user.getId())) return Color.WHITE;
        if (black != null && black.getId().equals(user.getId())) return Color.BLACK;
        return null;
    }

    public boolean isPlayer(User user) {
        return colorOf(user) != null;
    }

    public boolean hasClock() {
        return initialMs != null;
    }

    // ------------------------------------------------------------------ helpers

    private void flag(Color loser) {
        setStoredTime(loser, 0);
        finish(loser == Color.WHITE ? "0-1" : "1-0", "timeout");
    }

    private void finish(String result, String reason) {
        this.status = OnlineStatus.FINISHED;
        this.result = result;
        this.reason = reason;
    }

    private void setStoredTime(Color color, long ms) {
        if (color == Color.WHITE) whiteMs = ms; else blackMs = ms;
    }

    private static String describe(GameStatus status) {
        return switch (status) {
            case CHECKMATE -> "checkmate";
            case STALEMATE -> "stalemate";
            case THREEFOLD_REPETITION -> "threefold repetition";
            case FIFTY_MOVE_RULE -> "50-move rule";
            case INSUFFICIENT_MATERIAL -> "insufficient material";
            case ONGOING -> throw new IllegalStateException("not over");
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public User getCreator() { return creator; }
    public User getWhite() { return white; }
    public User getBlack() { return black; }
    public OnlineStatus getStatus() { return status; }
    public boolean isListed() { return listed; }
    public Long getInitialMs() { return initialMs; }
    public long getIncrementMs() { return incrementMs; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public Long getGameId() { return gameId; }
    public Instant getCreatedAt() { return createdAt; }
}
