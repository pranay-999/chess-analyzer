-- Migration #3: games that did NOT come from Chess.com (a pasted PGN or FEN).
-- Such a game has no Chess.com player and no Chess.com URL, so those columns become optional.
-- `source` says where a game came from; existing rows are all Chess.com imports.

ALTER TABLE games ALTER COLUMN player_id    DROP NOT NULL;
ALTER TABLE games ALTER COLUMN chesscom_url DROP NOT NULL;   -- UNIQUE still applies; Postgres allows many NULLs

ALTER TABLE games ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'CHESSCOM';
