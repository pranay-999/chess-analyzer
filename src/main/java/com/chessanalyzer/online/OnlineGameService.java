package com.chessanalyzer.online;

import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.PgnWriter;
import com.chessanalyzer.game.Game;
import com.chessanalyzer.game.GameMove;
import com.chessanalyzer.game.GameRepository;
import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.chess.PlayedMove;
import com.chessanalyzer.user.User;
import com.chessanalyzer.user.UserPrincipal;
import com.chessanalyzer.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Online games: load the game, let the entity apply the rules, save, then PUSH the
 * new state to everyone subscribed over the WebSocket:
 *
 *   /topic/games/{id}  every change of one game (both players and spectators listen)
 *   /topic/lobby       the list of open games changed
 *
 * Commands still arrive as normal REST calls (simple, authenticated, easy to test);
 * the WebSocket is only used for the server -> browser direction.
 */
@Service
@Transactional
public class OnlineGameService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ID_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";   // no look-alikes (l/1, o/0)
    private static final int MAX_WAITING_PER_USER = 3;

    private final OnlineGameRepository games;
    private final UserRepository users;
    private final GameRepository storedGames;
    private final SimpMessagingTemplate messaging;

    public OnlineGameService(OnlineGameRepository games, UserRepository users, GameRepository storedGames,
                             SimpMessagingTemplate messaging) {
        this.games = games;
        this.users = users;
        this.storedGames = storedGames;
        this.messaging = messaging;
    }

    /** @param minutes null = no clock;  @param color "WHITE", "BLACK" or "RANDOM" (the creator's side) */
    public OnlineGameView create(UserPrincipal me, Integer minutes, int incrementSeconds, String color, boolean listed) {
        if (minutes != null && (minutes < 1 || minutes > 60)) throw new IllegalArgumentException("Minutes: 1-60");
        if (incrementSeconds < 0 || incrementSeconds > 60) throw new IllegalArgumentException("Increment: 0-60 seconds");
        Color creatorColor = switch (color == null ? "RANDOM" : color) {
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "RANDOM" -> RANDOM.nextBoolean() ? Color.WHITE : Color.BLACK;
            default -> throw new IllegalArgumentException("Color must be WHITE, BLACK or RANDOM");
        };
        if (games.countByCreatorIdAndStatus(me.id(), OnlineStatus.WAITING) >= MAX_WAITING_PER_USER) {
            throw new IllegalArgumentException("You already have " + MAX_WAITING_PER_USER + " open games. Cancel one first");
        }
        OnlineGame game = OnlineGame.create(newPublicId(), user(me), creatorColor,
                minutes == null ? null : minutes * 60_000L, incrementSeconds * 1000L, listed);
        games.saveAndFlush(game);
        broadcastLobby();
        return OnlineGameView.of(game, Instant.now());
    }

    public OnlineGameView join(UserPrincipal me, String publicId) {
        OnlineGame game = load(publicId);
        game.join(user(me), Instant.now());
        return saveAndBroadcast(game, true);
    }

    public OnlineGameView move(UserPrincipal me, String publicId, String uci) {
        OnlineGame game = load(publicId);
        game.play(user(me), uci, Instant.now());
        return saveAndBroadcast(game, false);
    }

    public OnlineGameView resign(UserPrincipal me, String publicId) {
        OnlineGame game = load(publicId);
        game.resign(user(me));
        return saveAndBroadcast(game, false);
    }

    public OnlineGameView cancel(UserPrincipal me, String publicId) {
        OnlineGame game = load(publicId);
        game.cancel(user(me));
        return saveAndBroadcast(game, true);
    }

    /** Used by ClockChecker. Returns true if the game just ended on time. */
    public boolean flagIfTimeUp(String publicId) {
        OnlineGame game = load(publicId);
        if (!game.flagIfTimeUp(Instant.now())) return false;
        saveAndBroadcast(game, false);
        return true;
    }

    @Transactional(readOnly = true)
    public OnlineGameView get(String publicId) {
        return OnlineGameView.of(load(publicId), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<LobbyEntry> openGames() {
        return games.findByStatusAndListedTrueOrderByCreatedAtDesc(OnlineStatus.WAITING).stream()
                .map(LobbyEntry::of).toList();
    }

    @Transactional(readOnly = true)
    public List<OnlineGameView> myGames(UserPrincipal me) {
        Instant now = Instant.now();
        return games.findMine(me.id(), Set.of(OnlineStatus.WAITING, OnlineStatus.ACTIVE)).stream()
                .map(g -> OnlineGameView.of(g, now)).toList();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * saveAndFlush sends the UPDATE now (not at commit), so an optimistic-locking clash
     * throws HERE, before we tell anyone about a state that was never saved.
     */
    private OnlineGameView saveAndBroadcast(OnlineGame game, boolean lobbyChanged) {
        if (game.getStatus() == OnlineStatus.FINISHED && game.getGameId() == null) {
            game.attachStoredGame(storeForAnalysis(game));
        }
        games.saveAndFlush(game);
        OnlineGameView view = OnlineGameView.of(game, Instant.now());
        messaging.convertAndSend("/topic/games/" + game.getPublicId(), view);
        if (lobbyChanged) broadcastLobby();
        return view;
    }

    private void broadcastLobby() {
        messaging.convertAndSend("/topic/lobby", openGames());
    }

    /** Copies the finished game into "games" (as PGN + moves), so it opens in the analyser. */
    private long storeForAnalysis(OnlineGame game) {
        String white = game.getWhite().getUsername();
        String black = game.getBlack().getUsername();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Event", "Online game");
        headers.put("Site", "Chess Analyzer");
        headers.put("Date", LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        headers.put("White", white);
        headers.put("Black", black);
        headers.put("Termination", game.getReason());
        String pgn = PgnWriter.write(headers, game.uciMoves(), game.getResult());

        Game stored = Game.online(pgn, white, black, game.getResult(), Instant.now());
        for (PlayedMove m : PgnParser.parse(pgn).replay()) {
            stored.addMove(new GameMove(m.ply(), m.san(), m.uci(), m.fenAfter()));
        }
        return storedGames.save(stored).getId();
    }

    private OnlineGame load(String publicId) {
        return games.findByPublicId(publicId).orElseThrow(() -> new OnlineGameNotFoundException(publicId));
    }

    private User user(UserPrincipal me) {
        return users.findById(me.id()).orElseThrow(() -> new IllegalArgumentException("Your account no longer exists"));
    }

    private static String newPublicId() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 8; i++) id.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        return id.toString();
    }
}
