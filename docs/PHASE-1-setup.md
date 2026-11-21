# Phase 1 — Project skeleton, Postgres, Flyway

Goal: a Spring Boot app that starts, connects to Postgres, creates our tables,
and answers one HTTP request.

## Files created and why

| File | What it is | Why we need it |
|---|---|---|
| `pom.xml` | Maven build file | Lists Java version + libraries. Maven downloads them into `~/.m2` and builds the app. |
| `docker-compose.yml` | Postgres in Docker | A database for this project only, on port **5433** (your local Postgres already uses 5432). |
| `src/main/resources/application.yml` | App config | DB URL/user/password, server port, Stockfish path. |
| `src/main/resources/db/migration/V1__init_schema.sql` | First DB migration | Creates the 5 tables. Flyway runs it once and remembers that. |
| `src/main/java/com/chessanalyzer/ChessAnalyzerApplication.java` | `main()` method | Starts Spring. |
| `src/main/java/com/chessanalyzer/web/HealthController.java` | First REST endpoint | Proves browser → Spring → Postgres works. |

## What happens when you start the app (in order)

1. `main()` calls `SpringApplication.run(...)`.
2. Spring **scans** `com.chessanalyzer.**` for annotated classes (`@RestController` etc.).
3. **Auto-configuration**: Spring sees the Postgres driver in `pom.xml` and the
   `spring.datasource.*` settings, so it creates a connection pool (HikariCP)
   and a `JdbcTemplate` object.
4. **Flyway** runs: it compares `db/migration/` with the `flyway_schema_history`
   table and runs any SQL file not run before (first start: `V1`).
5. **Hibernate** starts with `ddl-auto: validate` — it only checks, never changes tables.
6. Spring creates `HealthController`, passing the `JdbcTemplate` into its
   constructor (**dependency injection**).
7. Embedded **Tomcat** starts on port 8080 and waits for requests.

## Request flow for `GET /api/health`

```
browser ──HTTP──► Tomcat ──► DispatcherServlet (Spring's router)
                               │ finds method with @GetMapping("/api/health")
                               ▼
                         HealthController.health()
                               │ jdbc.queryForObject("SELECT version()")
                               ▼
                           Postgres (Docker, port 5433)
                               │
       JSON ◄── Jackson converts the returned Map to JSON
```

## Key concepts learned

- **Bean**: an object Spring creates and manages. You ask for it in a constructor; you never `new` it.
- **Dependency injection**: constructor parameters are filled in by Spring. Makes classes easy to test (pass a fake instead).
- **Auto-configuration**: "convention over configuration" — add a library + a few properties, Spring wires it.
- **Migrations**: the schema is code, versioned in files. Never edit a migration that already ran; add `V2__...sql`.

## How to run it yourself

```bash
docker compose up -d            # start Postgres
mvn spring-boot:run             # start the app (Ctrl+C to stop)
curl localhost:8080/api/health  # or open it in a browser
```

## Connect pgAdmin

Add a new server: host `localhost`, port `5433`, database `chess`,
user `chess`, password `chess`. The tables are under Schemas → public → Tables.

## Try this (to learn)

- Add a second endpoint `GET /api/hello?name=Pranay` that returns `"Hello Pranay"`
  (hint: `@RequestParam String name`).
- Stop Docker (`docker compose stop`) and hit `/api/health` — read the error. Then start it again.
