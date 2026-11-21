package com.chessanalyzer.web;

import com.chessanalyzer.analysis.AnalysisNotFoundException;
import com.chessanalyzer.chesscom.PlayerNotFoundException;
import com.chessanalyzer.game.GameNotFoundException;
import com.chessanalyzer.online.OnlineGameNotFoundException;
import com.chessanalyzer.user.UsernameTakenException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler({GameNotFoundException.class, PlayerNotFoundException.class, AnalysisNotFoundException.class,
            OnlineGameNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Wrong username or password. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong username or password");
    }

    /**
     * 409 Conflict: the request clashes with the current state, e.g. a taken username, or two
     * moves arriving at the same moment (optimistic locking, see OnlineGame.version).
     */
    @ExceptionHandler({UsernameTakenException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail conflict(RuntimeException e) {
        String detail = e instanceof UsernameTakenException ? e.getMessage()
                : "Someone else changed this game at the same moment. Please try again.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    /** e.g. /api/games/abc (not a number) or ?page=-1 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ProblemDetail badRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
