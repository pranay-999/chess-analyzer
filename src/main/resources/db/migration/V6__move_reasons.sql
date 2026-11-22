-- Migration #6: the numbers behind each move's label, so the page can explain WHY a move
-- is Brilliant, Great, a Mistake... (Phase 18). Older analyses keep NULL here; re-analyse them.

ALTER TABLE move_evaluations ADD COLUMN win_drop          DOUBLE PRECISION;  -- winning chance lost (0-100)
ALTER TABLE move_evaluations ADD COLUMN material_given_up INT;               -- pawns' worth the move gave away
ALTER TABLE move_evaluations ADD COLUMN only_move_gap     DOUBLE PRECISION;  -- how much worse the 2nd-best move was
