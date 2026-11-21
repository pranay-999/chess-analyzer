package com.chessanalyzer.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity = a Java class whose objects are rows of a table.
 *
 * @Entity tells Hibernate to manage this class; @Table names the table.
 * Field names map to columns automatically: lastSyncedAt -> last_synced_at
 * (Spring's naming strategy turns camelCase into snake_case).
 * The table itself is created by Flyway (V1__init_schema.sql); Hibernate only
 * validates that this class matches it.
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // Postgres BIGSERIAL picks the id
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private Instant lastSyncedAt;

    /** JPA needs a no-argument constructor to build objects from rows. "protected" keeps our code from using it. */
    protected Player() {
    }

    public Player(String username) {
        this.username = username;
    }

    public void markSynced(Instant when) {
        this.lastSyncedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }
}
