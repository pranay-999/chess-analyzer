-- Migration #2: new columns needed by the analysis feature (Phase 5).
-- We never edit V1 (it already ran on your database); we add a new version instead.
-- Flyway sees V2 is new and runs it once at the next startup.

ALTER TABLE analyses ADD COLUMN analyzed_plies INT NOT NULL DEFAULT 0;   -- progress: moves done so far
ALTER TABLE analyses ADD COLUMN total_plies    INT NOT NULL DEFAULT 0;   -- progress: moves in the game
ALTER TABLE analyses ADD COLUMN error          TEXT;                     -- why it FAILED, if it did

ALTER TABLE move_evaluations ADD COLUMN best_move_san VARCHAR(10);      -- "Nf3", for display

CREATE INDEX idx_analyses_game ON analyses(game_id, created_at DESC);
