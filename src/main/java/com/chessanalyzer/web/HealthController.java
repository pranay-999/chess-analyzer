package com.chessanalyzer.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny endpoint to prove the whole chain works:
 * browser -> Tomcat -> this controller -> Postgres -> JSON response.
 *
 * @RestController = "this class handles HTTP requests, and whatever the methods
 * return is converted to JSON".
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    /**
     * Dependency injection: we never write `new JdbcTemplate(...)`.
     * Spring already built one (from the datasource settings in application.yml)
     * and passes it into this constructor when it creates the controller.
     */
    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** GET http://localhost:8080/api/health */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String dbVersion = jdbc.queryForObject("SELECT version()", String.class);
        Integer tableCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class);
        return Map.of(
                "status", "UP",
                "database", dbVersion,
                "tables", tableCount);
    }
}
