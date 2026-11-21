package com.chessanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the whole backend.
 *
 * @SpringBootApplication does three things:
 *  1. Component scan: finds every class in this package (and sub-packages)
 *     marked @RestController, @Service, @Repository, @Component... and creates
 *     one object ("bean") of each.
 *  2. Auto-configuration: looks at the libraries in pom.xml + application.yml
 *     and sets things up (web server, DB connection pool, Flyway, Hibernate).
 *  3. Marks this class as a configuration source.
 *
 * Because of (1), this class must sit in the TOP package (com.chessanalyzer);
 * everything else goes in sub-packages below it.
 */
@SpringBootApplication
public class ChessAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessAnalyzerApplication.class, args);
    }
}
