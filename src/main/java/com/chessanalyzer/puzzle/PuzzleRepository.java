package com.chessanalyzer.puzzle;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {

    boolean existsByGameIdAndPly(long gameId, int ply);

    // All queries below take an optional player (lower-case): null = puzzles from every game,
    // otherwise games synced for that Chess.com username OR any game where that name played
    // White or Black (games imported by link or PGN have no "player", only names).
    // "left join g.player": keeps games that have no player.

    /** Due puzzles, the most overdue first (call with PageRequest.of(0, 1) for "the next one"). */
    @Query("""
            select p from Puzzle p join fetch p.game g left join g.player pl
            where p.dueAt <= :now and (:player is null or pl.username = :player or lower(g.white) = :player or lower(g.black) = :player)
            order by p.dueAt asc""")
    List<Puzzle> findDue(String player, Instant now, Pageable page);

    @Query("""
            select count(p) from Puzzle p join p.game g left join g.player pl
            where p.dueAt <= :now and (:player is null or pl.username = :player or lower(g.white) = :player or lower(g.black) = :player)""")
    long countDue(String player, Instant now);

    @Query("""
            select count(p) from Puzzle p join p.game g left join g.player pl
            where :player is null or pl.username = :player or lower(g.white) = :player or lower(g.black) = :player""")
    long countAll(String player);

    /** When will the next puzzle be due (when none are due right now)? */
    @Query("""
            select min(p.dueAt) from Puzzle p join p.game g left join g.player pl
            where :player is null or pl.username = :player or lower(g.white) = :player or lower(g.black) = :player""")
    Instant nextDueAt(String player);
}
