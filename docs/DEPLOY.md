# Deploying Chess Analyzer (Render)

Goal: a public **HTTPS** address (like `https://chess-analyzer-xxxx.onrender.com`), so that:
- your phone can reach the app;
- Android can **install** it and show it in the **Share** menu (HTTPS required);
- the browser extension and your resume can point at a live demo.

Everything is prepared in the repo. These are the steps only you can do (accounts).

## What's already set up

| File | Role |
|---|---|
| `Dockerfile` | Builds React + Spring Boot and installs **Stockfish**, all in one image |
| `render.yaml` | **Blueprint**: one web service (our Dockerfile) + one PostgreSQL database, wired together |
| `src/main/resources/application-prod.yml` | `prod` profile: DB from env vars, `PORT`, HTTPS-aware (`forward-headers-strategy`), **secure** session cookie, smaller CPU budget |

Checked locally: the jar starts with the `prod` profile, reads the database from
environment variables, and its session cookie is `Secure; HttpOnly; SameSite=Lax`.

## Steps

1. **Push to GitHub.** Render deploys from your repository.
   Your token needs the **Workflows** permission because of `.github/workflows/ci.yml`
   (GitHub → Settings → Developer settings → your token → *Workflows: Read and write*).
   ```bash
   git push
   ```
2. **Create a Render account** at render.com and connect your GitHub account.
3. **New → Blueprint** → pick `chess-analyzer` → Render reads `render.yaml` → **Apply**.
   It creates the database and the web service. The first build takes ~10 minutes
   (Maven and npm download everything once).
4. Open the service's URL. Check `https://<your-app>.onrender.com/api/health` answers `"status":"UP"`.
   Flyway creates all tables on first start, so the database starts empty: sync your Chess.com games again.

## After deploying

- **Android**: open the URL in Chrome → ⋮ → **Install app**. After a game, in the
  Chess.com app: **Share** → **Chess Analyzer**.
- **iPhone**: open the URL in Safari → Share → **Add to Home Screen**. In the
  Chess.com app: **Share → Copy PGN** → our **📋 Paste from clipboard** button.
- **Extension**: toolbar icon → **Server** → paste your `https://…onrender.com` address → Save.
- **README**: add the live link at the top. Recruiters can click it.

## Good to know (free tier)

| Limit | What it means | Fix |
|---|---|---|
| Free web service **sleeps after 15 min** without visitors | The first visit afterwards takes ~1 min to wake up | Paid "Starter" plan stays awake |
| **512 MB RAM, a fraction of a CPU** | Stockfish analysis is several times slower than on your laptop | Use depth 12, or upgrade |
| Free **PostgreSQL expires after 30 days** | You'd lose the data | Upgrade the DB, or use a free Postgres from **Neon** / **Supabase** and set the `DB_*` variables yourself |
| Chess.com may block requests from cloud servers | Importing by **Chess.com link** could fail there | Share the **PGN** instead (always works). Lichess links use an official API. |

## How the production settings work

```
Browser ──HTTPS──► Render proxy ──HTTP + "X-Forwarded-Proto: https"──► Spring Boot (PORT)
```

- `server.forward-headers-strategy: framework` makes Spring trust that header, so it
  knows the visitor is on `https://` (correct absolute URLs, secure cookies).
- `server.servlet.session.cookie.secure: true` means the login cookie is never sent over plain HTTP.
- Secrets (the DB password) live **only** in Render's environment variables, never in Git.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` lets the JVM size itself to the container's memory.
