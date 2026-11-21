package com.chessanalyzer.analysis;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** The verdict on one played move (row in "move_evaluations"). */
@Entity
@Table(name = "move_evaluations")
public class MoveEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    private int ply;
    private Integer evalCp;        // after the move, white's view
    private Integer mateIn;        // after the move, white's view
    private String bestMoveUci;    // what the engine wanted instead (in the position before the move)
    private String bestMoveSan;
    private Integer cpLoss;

    @Enumerated(EnumType.STRING)
    private Classification classification;

    protected MoveEvaluation() {
    }

    public MoveEvaluation(int ply, Integer evalCp, Integer mateIn, String bestMoveUci, String bestMoveSan,
                          int cpLoss, Classification classification) {
        this.ply = ply;
        this.evalCp = evalCp;
        this.mateIn = mateIn;
        this.bestMoveUci = bestMoveUci;
        this.bestMoveSan = bestMoveSan;
        this.cpLoss = cpLoss;
        this.classification = classification;
    }

    void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public int getPly() { return ply; }
    public Integer getEvalCp() { return evalCp; }
    public Integer getMateIn() { return mateIn; }
    public String getBestMoveUci() { return bestMoveUci; }
    public String getBestMoveSan() { return bestMoveSan; }
    public Integer getCpLoss() { return cpLoss; }
    public Classification getClassification() { return classification; }
}
