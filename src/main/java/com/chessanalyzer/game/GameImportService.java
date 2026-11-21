package com.chessanalyzer.game;

import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.PgnGame;
import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.chess.PlayedMove;
import com.chessanalyzer.chesscom.ChessComClient;
import com.chessanalyzer.chesscom.ChessComGame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * Business logic for "import my games": Chess.com -> chess core -> Postgres.
 *
 * @Service marks this as a Spring bean holding business logic. It gets its three
 * collaborators through the constructor (dependency injection), so in tests we
 * can pass fakes instead of the real HTTP client and database.
 *
 * Transactions: we deliberately do NOT wrap the whole sync in one @Transactional.
 * A sync makes many slow HTTP calls; holding a DB transaction open across them is
 * bad practice. Instead every gameRepository.save(...) is its own small
 * transaction (Spring Data repository methods are transactional by default), and
 * thanks to cascade it saves the game AND all its moves together, or nothing.
 */
@Service
public class GameImportService {

    private static final Logger log = LoggerFactory.getLogger(GameImportService.class);

    private final ChessComClient chessCom;
    private final PlayerRepository players;
    private final GameRepository games;

    public GameImportService(ChessComClient chessCom, PlayerRepository players, GameRepository games) {
        this.chessCom = chessCom;
        this.players = players;
        this.games = games;
    }

    /**
     * Imports a player's games.
     *
     * @param months if given, only the most recent N monthly archives. If null: everything
     *               on the first sync, and only months since the last sync after that.
     */
    public SyncResult sync(String rawUsername, Integer months) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);   // Chess.com usernames are case-insensitive

        // Fetch first: if the user doesn't exist this throws 404 before we create anything.
        List<String> archives = chessCom.fetchArchiveUrls(username);

        Player player = players.findByUsername(username)
                .orElseGet(() -> players.save(new Player(username)));
        List<String> toFetch = selectArchives(archives, player.getLastSyncedAt(), months);

        int imported = 0, alreadyStored = 0, skipped = 0, failed = 0;
        for (String archiveUrl : toFetch) {
            log.info("Fetching {}", archiveUrl);
            for (ChessComGame g : chessCom.fetchGames(archiveUrl)) {
                if (!"chess".equals(g.rules()) || g.pgn() == null) {
                    skipped++;
                } else if (games.existsByChesscomUrl(g.url())) {
                    alreadyStored++;
                } else {
                    try {
                        games.save(toEntity(player, username, g));
                        imported++;
                    } catch (IllegalArgumentException e) {   // thrown by our PGN replay on a bad move
                        log.warn("Could not import {}: {}", g.url(), e.getMessage());
                        failed++;
                    }
                }
            }
        }

        player.markSynced(Instant.now());
        players.save(player);
        log.info("Synced {}: {} new games", username, imported);
        return new SyncResult(username, toFetch.size(), imported, alreadyStored, skipped, failed);
    }

    /**
     * Archive URLs end in /YYYY/MM and come oldest first. On a re-sync we start from the
     * month of the last sync (that month may have gained games since).
     */
    static List<String> selectArchives(List<String> archives, Instant lastSyncedAt, Integer months) {
        if (months != null && months > 0) {
            return archives.subList(Math.max(0, archives.size() - months), archives.size());
        }
        if (lastSyncedAt == null) {
            return archives;
        }
        YearMonth lastMonth = YearMonth.from(lastSyncedAt.atZone(ZoneOffset.UTC));
        return archives.stream()
                .filter(url -> !monthOf(url).isBefore(lastMonth))
                .toList();
    }

    private static YearMonth monthOf(String archiveUrl) {
        String[] parts = archiveUrl.split("/");
        return YearMonth.of(Integer.parseInt(parts[parts.length - 2]), Integer.parseInt(parts[parts.length - 1]));
    }

    /** Converts the Chess.com DTO into our entity, replaying the PGN to build every move. */
    private Game toEntity(Player player, String username, ChessComGame g) {
        PgnGame pgn = PgnParser.parse(g.pgn());
        List<PlayedMove> played = pgn.replay();   // throws IllegalArgumentException on an illegal move

        Color playerColor = username.equalsIgnoreCase(g.white().username()) ? Color.WHITE : Color.BLACK;
        String result = pgn.header("Result") != null ? pgn.header("Result") : "*";

        Game game = new Game(player, g.url(), g.pgn(),
                g.white().username(), g.black().username(),
                g.white().rating(), g.black().rating(),
                playerColor, result, g.timeClass(), pgn.header("ECO"),
                Instant.ofEpochSecond(g.endTime()));

        for (PlayedMove m : played) {
            game.addMove(new GameMove(m.ply(), m.san(), m.uci(), m.fenAfter()));
        }
        return game;
    }
}
