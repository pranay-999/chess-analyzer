package com.chessanalyzer;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @EnableAsync switches on @Async support. Without it, @Async is silently ignored
 * and AnalysisRunner.run would block the HTTP request until the whole game is analysed.
 *
 * Which thread pool runs @Async methods? Spring looks for a bean named "taskExecutor".
 * Spring Boot normally creates one from spring.task.execution in application.yml, but
 * ONLY if no other executor exists. Since Phase 10 the WebSocket support registers its
 * own pools, so Boot backed off and @Async quietly fell back to a basic executor with
 * no limit, which could start any number of Stockfish processes at once.
 * Declaring the pool here, built from the same YAML settings, makes the choice explicit.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Uses spring.task.execution.* (2 threads, names "analysis-1", "analysis-2"). */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }
}
