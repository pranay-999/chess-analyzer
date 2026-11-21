# Phase 7 — Integration tests, Docker, CI, README

Goal: make the project easy to trust (tests against real infrastructure), easy
to run (one command), and easy to show (README + green CI badge).

## 1. Integration test with Testcontainers (`AppIntegrationTest`)

Unit tests check classes one at a time with fakes. Some bugs only appear when the
real pieces meet: a Flyway column that doesn't match an entity, a transaction that
never commits, an `@Async` job that silently runs on the wrong thread. So one test
starts **the whole application**:

```
@Testcontainers + @Container   → starts a throw-away postgres:17 Docker container
@ServiceConnection             → Spring points spring.datasource.* at that container
@SpringBootTest(RANDOM_PORT)   → full app on a free port (Flyway, Hibernate, Tomcat, @Async)
@MockitoBean ChessComClient    → only the internet is faked
RestClient                     → real HTTP calls: sync → list → game → analyse → poll until DONE
```

It runs in about 10 seconds and needs Docker running. Testcontainers found Docker
Desktop's socket automatically.

## 2. One-command Docker (`Dockerfile`, `docker-compose.yml`)

The **multi-stage Dockerfile** builds in three steps and keeps only the result:

| Stage | Image | Produces |
|---|---|---|
| frontend | `node:22` | React build → `static/` |
| backend | `maven:3.9 + JDK 21` | `chess-analyzer.jar` (with the React files inside) |
| runtime | `eclipse-temurin:21-jre` + `apt install stockfish` | runs `java -jar app.jar` as a non-root user |

Copying `package.json` / `pom.xml` before the source code lets Docker **cache**
the dependency downloads, so rebuilding after a code change is fast.

`docker-compose.yml` uses a **profile**:

```bash
docker compose up -d                       # dev: Postgres only (what you've used so far)
docker compose --profile app up --build    # everything in Docker on :8080
APP_PORT=9090 docker compose --profile app up -d   # if 8080 is busy
```

Inside Docker's network the app reaches the database at `postgres:5432` (the
service name). The environment variable `SPRING_DATASOURCE_URL` overrides
`application.yml`, because Spring maps `SPRING_DATASOURCE_URL` to `spring.datasource.url`.
`depends_on: condition: service_healthy` waits for the Postgres healthcheck, so the
app never starts before the database is ready.

A bug I hit while building it: Dockerfile comments must be on their **own line**.
`USER ubuntu   # comment` made the build fail.

## 3. CI (`.github/workflows/ci.yml`)

On every push to `main` and every pull request, GitHub runs two jobs:
- **Java tests**: installs Stockfish, runs `mvn test` (including the
  Testcontainers test, using the runner's Docker).
- **React build**: `npm ci`, lint (`oxlint`), type-check, and build.

See the results under the **Actions** tab on GitHub.

## 4. README

The top-level `README.md` is what recruiters see first: screenshots,
features, an architecture diagram (Mermaid, which GitHub renders), design decisions,
how to run it, the API table, and testing. Screenshots are in `docs/images/` and
were taken with headless Chrome:

```bash
google-chrome --headless=new --window-size=1280,1000 --virtual-time-budget=8000 \
  --screenshot=docs/images/game-analysis.png http://localhost:8080/games/345
```

## Try this

1. After pushing, open the **Actions** tab and watch both jobs run. The README badge
   turns green when they pass (or red, with the failing step's log one click away).
2. Break a Flyway migration on purpose (rename a column in a new `V3__*.sql`) and run
   `mvn test`. Only `AppIntegrationTest` catches it.
3. Retake the screenshots with your own games.
