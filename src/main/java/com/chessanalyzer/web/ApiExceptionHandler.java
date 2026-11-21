package com.chessanalyzer.web;

import com.chessanalyzer.analysis.AnalysisNotFoundException;
import com.chessanalyzer.chesscom.PlayerNotFoundException;
import com.chessanalyzer.game.GameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * One place that turns exceptions into HTTP error responses, for every controller.
 * Without this, any exception becomes a generic "500 Internal Server Error".
 *
 * @RestControllerAdvice = "apply these @ExceptionHandler methods to all controllers".
 *
 * ProblemDetail is the standard JSON error format (RFC 9457):
 *   {"type":"about:blank","title":"Not Found","status":404,"detail":"Game not found: 99","instance":"/api/games/99"}
 * The frontend can show `detail` to the user.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({GameNotFoundException.class, PlayerNotFoundException.class, AnalysisNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** e.g. /api/games/abc (not a number) or ?page=-1 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ProblemDetail badRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
