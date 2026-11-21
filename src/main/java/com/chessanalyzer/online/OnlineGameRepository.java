package com.chessanalyzer.online;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlineGameRepository extends JpaRepository<OnlineGame, Long> {

    /** Loads the players too (one JOIN query), since almost every view needs their names. */
    @EntityGraph(attributePaths = {"creator", "white", "black"})
    Optional<OnlineGame> findByPublicId(String publicId);

    /** The lobby: games waiting for an opponent, newest first. */
    @EntityGraph(attributePaths = {"creator", "white", "black"})
    List<OnlineGame> findByStatusAndListedTrueOrderByCreatedAtDesc(OnlineStatus status);

    /** Your games that aren't over yet, so you can get back into them. */
    @EntityGraph(attributePaths = {"creator", "white", "black"})
    @Query("""
            select g from OnlineGame g
            where g.status in :statuses
              and (g.creator.id = :userId or g.white.id = :userId or g.black.id = :userId)
            order by g.createdAt desc""")
    List<OnlineGame> findMine(long userId, Collection<OnlineStatus> statuses);

    long countByCreatorIdAndStatus(long creatorId, OnlineStatus status);

    /** Running games with a clock: ClockChecker looks at these every second. */
    @Query("select g.publicId from OnlineGame g where g.status = com.chessanalyzer.online.OnlineStatus.ACTIVE and g.initialMs is not null")
    List<String> findActiveTimedGameIds();
}
