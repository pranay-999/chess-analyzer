package com.chessanalyzer.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An account on OUR site (not Chess.com). Guests are real rows too, so online
 * games can point at them; they just have no password.
 *
 * The password is never stored, only a BCrypt hash: a one-way, deliberately slow
 * function. Even we can't turn the hash back into the password.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String passwordHash;

    @Column(nullable = false)
    private boolean guest;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    private User(String username, String passwordHash, boolean guest) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.guest = guest;
    }

    public static User registered(String username, String passwordHash) {
        return new User(username, passwordHash, false);
    }

    public static User guest(String username) {
        return new User(username, null, true);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isGuest() { return guest; }
    public Instant getCreatedAt() { return createdAt; }
}
