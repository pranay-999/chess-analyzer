package com.chessanalyzer.game;

import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chesscom.ChessComClient;
import com.chessanalyzer.chesscom.ChessComGame;
import com.chessanalyzer.chesscom.ChessComGame.ChessComPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of the service with Mockito "mocks": fake versions of the client and
 * repositories whose answers we script with when(...).thenReturn(...).
 * No network, no database, no Spring — so it runs in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class GameImportServiceTest {

    private static final String ARCHIVE = "https://api.chess.com/pub/player/alice/games/2026/11";

    @Mock ChessComClient chessCom;
    @Mock PlayerRepository players;
    @Mock GameRepository games;
    @InjectMocks GameImportService service;   // Mockito builds the service with the three mocks above

    @Test
    void importsNewGamesAndSkipsVariantsAndDuplicates() {
        Player alice = new Player("alice");
        when(chessCom.fetchArchiveUrls("alice")).thenReturn(List.of(ARCHIVE));
        when(players.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(chessCom.fetchGames(ARCHIVE)).thenReturn(List.of(
                game("https://www.chess.com/game/live/1", "chess", "1. e4 e5 2. Nf3 Nc6 1-0"),
                game("https://www.chess.com/game/live/2", "chess960", "1. e4 e5 *"),
                game("https://www.chess.com/game/live/3", "chess", "1. d4 d5 *")));
        when(games.existsByChesscomUrl("https://www.chess.com/game/live/1")).thenReturn(false);
        when(games.existsByChesscomUrl("https://www.chess.com/game/live/3")).thenReturn(true);

        // Username typed with capitals/spaces is normalised.
        SyncResult result = service.sync("  Alice ", null);

        assertEquals(new SyncResult("alice", 1, 1, 1, 1, 0), result);

        // ArgumentCaptor grabs the Game object the service passed to save(...), so we can inspect it.
        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(games).save(saved.capture());
        Game game = saved.getValue();
        assertEquals(Color.BLACK, game.getPlayerColor());   // alice was black in our fake game
        assertEquals("1-0", game.getResult());
        assertEquals(4, game.getMoves().size());
        assertEquals("g1f3", game.getMoves().get(2).getUci());
        assertEquals(Instant.ofEpochSecond(1763650000L), game.getPlayedAt());
    }

    @Test
    void illegalPgnIsCountedAsFailedNotSaved() {
        when(chessCom.fetchArchiveUrls("alice")).thenReturn(List.of(ARCHIVE));
        when(players.findByUsername("alice")).thenReturn(Optional.of(new Player("alice")));
        when(chessCom.fetchGames(ARCHIVE)).thenReturn(List.of(
                game("https://www.chess.com/game/live/9", "chess", "1. e4 e5 2. Ke3 *")));

        SyncResult result = service.sync("alice", null);

        assertEquals(1, result.failed());
        verify(games, never()).save(any());
    }

    @Test
    void selectsArchives() {
        List<String> all = List.of(
                "https://x/games/2026/09", "https://x/games/2026/10", "https://x/games/2026/11");

        assertEquals(all, GameImportService.selectArchives(all, null, null));
        assertEquals(all.subList(1, 3), GameImportService.selectArchives(all, null, 2));
        // Last synced on 15 Oct 2026 -> refetch October (may have new games) and November.
        Instant lastSync = Instant.parse("2026-10-15T12:00:00Z");
        assertEquals(all.subList(1, 3), GameImportService.selectArchives(all, lastSync, null));
    }

    private static ChessComGame game(String url, String rules, String moves) {
        String pgn = "[White \"bob\"]\n[Black \"alice\"]\n[Result \"1-0\"]\n\n" + moves;
        return new ChessComGame(url, pgn, "blitz", rules, 1763650000L,
                new ChessComPlayer("bob", 1500, "win"),
                new ChessComPlayer("alice", 1480, "resigned"));
    }
}
