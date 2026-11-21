package com.chessanalyzer.analysis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    /** The newest analysis of a game by one engine ("First" = LIMIT 1). */
    Optional<Analysis> findFirstByGameIdAndEngineOrderByCreatedAtDesc(Long gameId, String engine);

    /** Ids of all finished analyses by one engine (used to make puzzles from older analyses). */
    @Query("select a.id from Analysis a where a.status = com.chessanalyzer.analysis.AnalysisStatus.DONE and a.engine = :engine")
    List<Long> findDoneIds(String engine);

    /** Loads the analysis, its game AND the game's moves in one go: everything the job needs. */
    @EntityGraph(attributePaths = {"game", "game.moves"})
    Optional<Analysis> findWithGameMovesById(Long id);

    // ---- small UPDATE statements --------------------------------------------------
    // @Query with JPQL (queries on entity classes, not tables). @Modifying says it's an
    // UPDATE, not a SELECT. Each runs in its own short transaction (@Transactional),
    // so the progress is visible to other requests immediately.

    @Transactional
    @Modifying
    @Query("update Analysis a set a.status = com.chessanalyzer.analysis.AnalysisStatus.RUNNING, "
            + "a.totalPlies = :totalPlies where a.id = :id")
    void markRunning(Long id, int totalPlies);

    @Transactional
    @Modifying
    @Query("update Analysis a set a.analyzedPlies = :analyzedPlies where a.id = :id")
    void updateProgress(Long id, int analyzedPlies);

    @Transactional
    @Modifying
    @Query("update Analysis a set a.status = com.chessanalyzer.analysis.AnalysisStatus.FAILED, "
            + "a.error = :error where a.id = :id")
    void markFailed(Long id, String error);

    /** Used at startup: jobs that were running when the server stopped will never finish. */
    @Transactional
    @Modifying
    @Query("update Analysis a set a.status = com.chessanalyzer.analysis.AnalysisStatus.FAILED, a.error = :error "
            + "where a.status in (com.chessanalyzer.analysis.AnalysisStatus.PENDING, "
            + "com.chessanalyzer.analysis.AnalysisStatus.RUNNING)")
    int failAllInProgress(String error);
}
