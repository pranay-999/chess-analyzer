package com.chessanalyzer;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @EnableAsync switches on @Async support. Without it, @Async is silently ignored
 * and AnalysisRunner.run would block the HTTP request until the whole game is analysed.
 * Spring Boot supplies the thread pool (see spring.task.execution in application.yml).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
