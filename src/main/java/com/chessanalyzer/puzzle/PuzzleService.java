package com.chessanalyzer.puzzle;

import com.chessanalyzer.analysis.Analysis;
import com.chessanalyzer.analysis.AnalysisRepository;
import com.chessanalyzer.analysis.Classification;
import com.chessanalyzer.analysis.MoveAnalyzer;
import com.chessanalyzer.analysis.MoveEvaluation;
import com.chessanalyzer.chess.Board;
import com.chessanalyzer.chess.Color;
import com.chessanalyzer.chess.Move;
import com.chessanalyzer.chess.PgnParser;
import com.chessanalyzer.chess.San;
import com.chessanalyzer.engine.Engine;
import com.chessanalyzer.engine.EngineFactory;
import com.chessanalyzer.engine.Evaluation;
import com.chessanalyzer.game.Game;
import com.chessanalyzer.game.GameMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns your analysed mistakes into puzzles, serves the next one that's due, and
 * checks your answers.
 */
@Service
@Transactional
public class PuzzleService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleService.class);
    private static final Set<Classification> PUZZLE_WORTHY = Set.of(Classification.MISTAKE, Classification.BLUNDER);
    /** An answer other than Stockfish's still counts if it loses less than this much winning chance. */
    static final double ALSO_GOOD_MAX_DROP = 5.0;
    private static final int CHECK_DEPTH = 12;

    private final PuzzleRepository puzzles;
    private final AnalysisRepository analyses;
    private final EngineFactory engines;

    public PuzzleService(PuzzleRepository puzzles, AnalysisRepository analyses, EngineFactory engines) {
        this.puzzles = puzzles;
        this.analyses = analyses;
        this.engines = engines;
    }

    // ------------------------------------------------------------------ making puzzles

    /**
     * Called when an analysis finishes. Only Stockfish analyses (our own engine is too
     * weak to be trusted as a teacher), and only YOUR moves (Game.playerColor).
     * Safe to call twice: puzzles that already exist are skipped.
     * @return how many new puzzles were created
     */
    public int createFromAnalysis(long analysisId) {
        Analysis analysis = analyses.findWithGameMovesById(analysisId).orElseThrow();
        if (!"stockfish".equals(analysis.getEngine())) return 0;
        Game game = analysis.getGame();
        List<GameMove> moves = game.getMoves();
        String startFen = PgnParser.parse(game.getPgn()).startingBoard().toFen();

        Map<Integer, MoveEvaluation> evalByPly = new HashMap<>();
        analysis.getEvaluations().forEach(e -> evalByPly.put(e.getPly(), e));

        int created = 0;
        for (MoveEvaluation e : analysis.getEvaluations()) {
            int ply = e.getPly();
            if (!PUZZLE_WORTHY.contains(e.getClassification()) || e.getBestMoveUci() == null) continue;
            String fen = ply == 1 ? startFen : moves.get(ply - 2).getFenAfter();   // position before the move
            Board board = Board.fromFen(fen);
            if (board.sideToMove() != game.getPlayerColor()) continue;             // the opponent's mistake: not your puzzle
            GameMove played = moves.get(ply - 1);
            if (played.getUci().equals(e.getBestMoveUci()) || puzzles.existsByGameIdAndPly(game.getId(), ply)) continue;

            // The engine's score of this position = the evaluation stored after the PREVIOUS move.
            MoveEvaluation before = evalByPly.get(ply - 1);
            puzzles.save(new Puzzle(game, ply, fen, e.getBestMoveUci(), e.getBestMoveSan(),
                    played.getUci(), played.getSan(), e.getClassification(),
                    before == null ? null : before.getEvalCp(), before == null ? null : before.getMateIn()));
            created++;
        }
        return created;
    }

    /** At startup: make puzzles from games analysed before this feature existed. */
    @EventListener(ApplicationReadyEvent.class)
    public void createFromExistingAnalyses() {
        int created = 0;
        for (long id : analyses.findDoneIds("stockfish")) {
            created += createFromAnalysis(id);
        }
        if (created > 0) log.info("Created {} puzzles from earlier analyses", created);
    }

    // ------------------------------------------------------------------ solving

    public record NextPuzzle(PuzzleView puzzle, long due, long total, Instant nextDueAt) {
    }

    /** The puzzle on screen. The solution is deliberately NOT included: no peeking in the network tab. */
    public record PuzzleView(long id, String fen, Color sideToMove, String playedSan, Classification classification,
                             long gameId, String white, String black, Instant playedAt, int attempts, int solved) {
    }

    public record AttemptResult(boolean correct, boolean exactMatch, String yourMoveSan,
                                String solutionUci, String solutionSan, String playedSan, Instant nextDueAt) {
    }

    /** @param player a Chess.com username, or null for puzzles from all your games */
    @Transactional(readOnly = true)
    public NextPuzzle next(String player) {
        String who = player == null || player.isBlank() ? null : player.trim().toLowerCase();
        Instant now = Instant.now();
        PuzzleView view = puzzles.findDue(who, now, PageRequest.of(0, 1)).stream().findFirst().map(p -> {
            Game g = p.getGame();
            return new PuzzleView(p.getId(), p.getFen(), Board.fromFen(p.getFen()).sideToMove(), p.getPlayedSan(),
                    p.getClassification(), g.getId(), g.getWhite(), g.getBlack(), g.getPlayedAt(),
                    p.getAttempts(), p.getSolved());
        }).orElse(null);
        return new NextPuzzle(view, puzzles.countDue(who, now), puzzles.countAll(who), puzzles.nextDueAt(who));
    }

    public AttemptResult attempt(long puzzleId, String uci) {
        Puzzle puzzle = load(puzzleId);
        Board board = Board.fromFen(puzzle.getFen());
        Move move = Move.fromUci(uci);
        if (!board.legalMoves().contains(move)) {
            throw new IllegalArgumentException("That move isn't legal here");
        }
        boolean exact = uci.equals(puzzle.getSolutionUci());
        boolean correct = exact || isAlmostAsGood(puzzle, board, move);
        puzzle.recordAttempt(correct, Instant.now());   // dirty checking saves it at commit
        return new AttemptResult(correct, exact, San.toSan(board, move), puzzle.getSolutionUci(),
                puzzle.getSolutionSan(), puzzle.getPlayedSan(), puzzle.getDueAt());
    }

    /** "Show solution": counts as not solved, so the puzzle comes back soon. */
    public AttemptResult reveal(long puzzleId) {
        Puzzle puzzle = load(puzzleId);
        puzzle.recordAttempt(false, Instant.now());
        return new AttemptResult(false, false, null, puzzle.getSolutionUci(), puzzle.getSolutionSan(),
                puzzle.getPlayedSan(), puzzle.getDueAt());
    }

    /**
     * Often there are two good moves. Ask Stockfish about the position after YOUR move:
     * if your winning chance is (almost) as high as before, you get the credit.
     */
    private boolean isAlmostAsGood(Puzzle puzzle, Board board, Move move) {
        if (puzzle.getEvalBeforeCp() == null && puzzle.getEvalBeforeMate() == null) return false;
        Color me = board.sideToMove();
        Board after = board.makeMove(move);
        Evaluation evalAfter;
        try (Engine engine = engines.open("stockfish")) {
            evalAfter = engine.evaluate(after.toFen(), CHECK_DEPTH);
        }
        Evaluation evalBefore = new Evaluation(puzzle.getEvalBeforeCp(), puzzle.getEvalBeforeMate(), null);
        double before = forMe(me, MoveAnalyzer.whiteWinPercent(evalBefore, me));
        double afterPct = forMe(me, MoveAnalyzer.whiteWinPercent(evalAfter, me.opposite()));
        return before - afterPct < ALSO_GOOD_MAX_DROP;
    }

    private static double forMe(Color me, double whitePercent) {
        return me == Color.WHITE ? whitePercent : 100 - whitePercent;
    }

    private Puzzle load(long id) {
        return puzzles.findById(id).orElseThrow(() -> new IllegalArgumentException("Puzzle " + id + " not found"));
    }
}
