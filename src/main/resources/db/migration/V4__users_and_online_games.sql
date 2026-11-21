-- Migration #4: accounts and live games between two people (Phase 10).

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    password_hash VARCHAR(100),                       -- BCrypt hash; NULL for guests (they can't log in again)
    guest         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE online_games (
    id              BIGSERIAL PRIMARY KEY,
    public_id       VARCHAR(12) NOT NULL UNIQUE,      -- random id used in invite links (/online/k3f9x2qp)
    creator_id      BIGINT      NOT NULL REFERENCES users(id),
    white_id        BIGINT      REFERENCES users(id), -- NULL until someone takes that side
    black_id        BIGINT      REFERENCES users(id),
    status          VARCHAR(10) NOT NULL,             -- WAITING / ACTIVE / FINISHED / ABORTED
    listed          BOOLEAN     NOT NULL,             -- shown in the public lobby?
    initial_ms      BIGINT,                           -- NULL = no clock
    increment_ms    BIGINT      NOT NULL DEFAULT 0,
    white_ms        BIGINT,                           -- time left (at turn_started_at)
    black_ms        BIGINT,
    turn_started_at TIMESTAMPTZ,                      -- when the side to move started thinking
    moves           TEXT        NOT NULL DEFAULT '',  -- UCI moves separated by spaces: "e2e4 e7e5"
    result          VARCHAR(8),                       -- 1-0 / 0-1 / 1/2-1/2
    reason          VARCHAR(30),                      -- checkmate / timeout / resignation / ...
    game_id         BIGINT      REFERENCES games(id), -- stored copy for the analyser, once finished
    version         BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking (see OnlineGame.java)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_online_games_status ON online_games(status);
