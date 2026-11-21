package com.chessanalyzer.game;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Read-only queries for the game pages.
 *
 * @Transactional(readOnly = true) on the class applies to every public method:
 *  - the DB session stays open for the whole method, so touching LAZY fields
 *    (game.getPlayer(), game.getMoves()) works. Outside a transaction they throw
 *    LazyInitializationException — the most common JPA error beginners hit;
 *  - readOnly tells Hibernate it can skip checking entities for changes (faster).
 *
 * We convert entities to DTOs INSIDE the transaction, and the controller only ever
 * sees DTOs.
 */
@Service
@Transactional(readOnly = true)
public class GameService {

    private static final int MAX_PAGE_SIZE = 100;

    private final GameRepository games;

    public GameService(GameRepository games) {
        this.games = games;
    }

    public PageResponse<GameSummary> listGames(String username, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));   // throws IllegalArgumentException if page < 0 or size < 1
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return PageResponse.from(games.findByPlayerUsernameOrderByPlayedAtDesc(normalized, pageRequest)
                .map(GameSummary::from));
    }

    public GameDetail getGame(long id) {
        Game game = games.findWithMovesById(id).orElseThrow(() -> new GameNotFoundException(id));
        return GameDetail.from(game);
    }
}
