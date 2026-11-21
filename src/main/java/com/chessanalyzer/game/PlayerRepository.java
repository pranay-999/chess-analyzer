package com.chessanalyzer.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository: we only write the interface, Spring generates the class
 * at startup. JpaRepository<Player, Long> already gives save(), findById(),
 * findAll(), delete()... for the Player entity whose id is a Long.
 *
 * Extra queries are written as method names: "findByUsername" becomes
 * SELECT * FROM players WHERE username = ?
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUsername(String username);
}
