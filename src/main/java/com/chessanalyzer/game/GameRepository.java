package com.chessanalyzer.game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    /** SELECT EXISTS(... WHERE chesscom_url = ?) — used to skip games we already imported. */
    boolean existsByChesscomUrl(String chesscomUrl);

    /**
     * Spring Data reads the method name: games whose player.username = ?, newest first.
     * Pageable adds LIMIT/OFFSET; Page also runs a COUNT query so we know the total.
     */
    Page<Game> findByPlayerUsernameOrderByPlayedAtDesc(String username, Pageable pageable);

    /**
     * Loads a game AND its moves in ONE query (a JOIN). Without @EntityGraph, the
     * LAZY moves list would need a second query the first time we touch it.
     * ("WithMoves" in the name is just a label; Spring ignores words between find and By.)
     */
    @EntityGraph(attributePaths = "moves")
    Optional<Game> findWithMovesById(Long id);
}
