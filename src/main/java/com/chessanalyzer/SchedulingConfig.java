package com.chessanalyzer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled methods (online/ClockChecker runs every second).
 *
 * @ConditionalOnProperty: this whole config only exists when chess.scheduling.enabled
 * is true (the default). Integration tests switch it off, because Spring keeps their
 * app contexts alive after each test's throw-away database is gone.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "chess.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
