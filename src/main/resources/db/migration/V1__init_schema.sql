-- Flyway migration #1.
-- Flyway runs every V<number>__<name>.sql file ONCE, in order, and records it in
-- the table "flyway_schema_history". Never edit this file after it has run:
-- to change the schema later, add V2__something.sql instead.

CREATE TABLE players (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(50) NOT NULL UNIQUE,     -- chess.com username (lowercase)
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE games (
    id            BIGSERIAL PRIMARY KEY,
    player_id     BIGINT       NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    chesscom_url  VARCHAR(255) NOT NULL UNIQUE,     -- prevents importing a game twice
    pgn           TEXT         NOT NULL,            -- raw game text, the source of truth
    white         VARCHAR(50)  NOT NULL,
    black         VARCHAR(50)  NOT NULL,
    white_rating  INT,
    black_rating  INT,
    player_color  VARCHAR(5)   NOT NULL,            -- 'WHITE' or 'BLACK' (which side YOU played)
    result        VARCHAR(10)  NOT NULL,            -- '1-0', '0-1', '1/2-1/2'
    time_class    VARCHAR(10),                      -- bullet / blitz / rapid / daily
    opening_eco   VARCHAR(10),
    played_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_games_player_played ON games(player_id, played_at DESC);

CREATE TABLE moves (
    id        BIGSERIAL PRIMARY KEY,
    game_id   BIGINT      NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    ply       INT         NOT NULL,                 -- half-move number, 1 = white's first move
    san       VARCHAR(10) NOT NULL,                 -- human notation, e.g. "Nf3"
    uci       VARCHAR(5)  NOT NULL,                 -- engine notation, e.g. "g1f3"
    fen_after VARCHAR(100) NOT NULL,                -- board position after this move
    UNIQUE (game_id, ply)
);

CREATE TABLE analyses (
    id             BIGSERIAL PRIMARY KEY,
    game_id        BIGINT      NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    engine         VARCHAR(30) NOT NULL,            -- 'stockfish' or 'my-engine'
    depth          INT         NOT NULL,
    status         VARCHAR(10) NOT NULL,            -- PENDING / RUNNING / DONE / FAILED
    accuracy_white NUMERIC(5,2),
    accuracy_black NUMERIC(5,2),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE move_evaluations (
    id             BIGSERIAL PRIMARY KEY,
    analysis_id    BIGINT      NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    ply            INT         NOT NULL,
    eval_cp        INT,                             -- centipawns from white's view (100 = one pawn)
    mate_in        INT,                             -- used instead of eval_cp when mate is forced
    best_move_uci  VARCHAR(5),
    cp_loss        INT,                             -- how much worse the played move was
    classification VARCHAR(12),                     -- BEST / GOOD / INACCURACY / MISTAKE / BLUNDER
    UNIQUE (analysis_id, ply)
);
