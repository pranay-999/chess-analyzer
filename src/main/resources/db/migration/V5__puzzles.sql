-- Migration #5: puzzles made from YOUR mistakes and blunders (Phase 16).
-- One row = "in this position you played X; find the better move", with its own
-- spaced-repetition schedule (solve it -> it comes back later and later).

CREATE TABLE puzzles (
    id               BIGSERIAL PRIMARY KEY,
    game_id          BIGINT       NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    ply              INT          NOT NULL,          -- the move you got wrong (1 = first move of the game)
    fen              VARCHAR(100) NOT NULL,          -- the position BEFORE that move (your turn)
    solution_uci     VARCHAR(5)   NOT NULL,          -- what Stockfish wanted
    solution_san     VARCHAR(10)  NOT NULL,
    played_uci       VARCHAR(5)   NOT NULL,          -- what you actually played
    played_san       VARCHAR(10)  NOT NULL,
    classification   VARCHAR(12)  NOT NULL,          -- MISTAKE or BLUNDER
    eval_before_cp   INT,                            -- engine score of the position (white's view),
    eval_before_mate INT,                            -- used to judge other good answers
    interval_days    INT          NOT NULL DEFAULT 0,
    due_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    attempts         INT          NOT NULL DEFAULT 0,
    solved           INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (game_id, ply)
);

CREATE INDEX idx_puzzles_due ON puzzles(due_at);
