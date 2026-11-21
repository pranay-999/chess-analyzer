package com.chessanalyzer.online;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * If a player simply stops moving, no request ever arrives that would notice their
 * clock ran out. So every second this job checks all running timed games and ends
 * the ones whose side to move is out of time. Both players see it at once via the WebSocket.
 *
 * @Scheduled needs @EnableScheduling (see SchedulingConfig).
 */
@Component
public class ClockChecker {

    private static final Logger log = LoggerFactory.getLogger(ClockChecker.class);

    private final OnlineGameRepository games;
    private final OnlineGameService service;

    public ClockChecker(OnlineGameRepository games, OnlineGameService service) {
        this.games = games;
        this.service = service;
    }

    @Scheduled(fixedDelay = 1000)   // 1 s after the previous run finished
    public void checkClocks() {
        for (String id : games.findActiveTimedGameIds()) {
            try {
                if (service.flagIfTimeUp(id)) {   // each call is its own transaction (@Transactional on the service)
                    log.info("Online game {} ended on time", id);
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // A player moved at the same instant; next run will look again.
            }
        }
    }
}
