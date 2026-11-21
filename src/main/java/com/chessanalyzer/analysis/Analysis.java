package com.chessanalyzer.analysis;

import com.chessanalyzer.game.Game;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One engine analysis run of one game (row in "analyses") with its per-move results. */
@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(nullable = false)
    private String engine;

    @Column(nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(precision = 5, scale = 2)   // NUMERIC(5,2) -> BigDecimal (exact decimals)
    private BigDecimal accuracyWhite;

    @Column(precision = 5, scale = 2)
    private BigDecimal accuracyBlack;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int analyzedPlies;

    @Column(nullable = false)
    private int totalPlies;

    @Column(columnDefinition = "text")
    private String error;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ply")
    private List<MoveEvaluation> evaluations = new ArrayList<>();

    protected Analysis() {
    }

    public Analysis(Game game, String engine, int depth) {
        this.game = game;
        this.engine = engine;
        this.depth = depth;
        this.status = AnalysisStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public boolean isInProgress() {
        return status == AnalysisStatus.PENDING || status == AnalysisStatus.RUNNING;
    }

    /** Stores the finished results. Called once, at the end of the job. */
    public void complete(List<MoveEvaluation> results, BigDecimal accuracyWhite, BigDecimal accuracyBlack) {
        evaluations.clear();
        for (MoveEvaluation e : results) {
            e.setAnalysis(this);
            evaluations.add(e);
        }
        this.accuracyWhite = accuracyWhite;
        this.accuracyBlack = accuracyBlack;
        this.totalPlies = results.size();
        this.analyzedPlies = results.size();
        this.status = AnalysisStatus.DONE;
        this.error = null;
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public String getEngine() { return engine; }
    public int getDepth() { return depth; }
    public AnalysisStatus getStatus() { return status; }
    public BigDecimal getAccuracyWhite() { return accuracyWhite; }
    public BigDecimal getAccuracyBlack() { return accuracyBlack; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAnalyzedPlies() { return analyzedPlies; }
    public int getTotalPlies() { return totalPlies; }
    public String getError() { return error; }
    public List<MoveEvaluation> getEvaluations() { return evaluations; }
}
