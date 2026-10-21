# StatDugout API

[![CI](https://github.com/zsheppard78/mlb-stats-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/zsheppard78/mlb-stats-backend/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-336791)

The backend for **[StatDugout](https://mlb-stats-frontend.onrender.com)**, an MLB stats web app. It handles accounts (username + password, or Sign in with Google) and saves each person's favorite teams, so they follow you to any device.

Frontend repo: [zsheppard78/mlb-stats-frontend](https://github.com/zsheppard78/mlb-stats-frontend)

## Architecture

```
Browser (StatDugout, vanilla JS)
   │
   ├── Stats, standings, scores ──► MLB Stats API (directly, no backend needed)
   │
   └── Accounts and favorites ───► this API (Spring Boot, Docker on Render)
                                        │
                                        ├── PostgreSQL on Neon (Flyway migrations)
                                        └── Google tokeninfo (verifies Google sign-ins)
```

- **Stateless REST API.** Sign-in returns a random bearer token; the browser sends it as `Authorization: Bearer …`. Tokens work across the frontend's and backend's separate domains without third-party cookies.
- **Plain JDBC.** Three small tables don't need an ORM, so queries use `JdbcTemplate` directly. That keeps startup fast and memory low on Render's 512 MB free plan.
- **Schema as code.** Flyway creates and updates the tables from versioned SQL files on startup.
- **Runs anywhere.** With no `DATABASE_URL`, the app falls back to an in-memory H2 database, so it starts with zero setup locally and in tests.

## Endpoints

| Method | Path | Auth | What it does |
|---|---|---|---|
| `GET` | `/api/ping` | – | Liveness check; doesn't touch the database |
| `GET` | `/api/health` | – | Database connection and schema status |
| `GET` | `/api/auth/config` | – | Which sign-in options are available (and the Google client id) |
| `POST` | `/api/auth/register` | – | `{username, password}` → account + token |
| `POST` | `/api/auth/login` | – | `{username, password}` → token |
| `POST` | `/api/auth/google` | – | `{credential}` from the Google button → token |
| `POST` | `/api/auth/logout` | Bearer | Ends this session |
| `GET` | `/api/me` | Bearer | The signed-in account and its favorite teams |
| `GET` | `/api/favorites` | Bearer | Favorite team ids |
| `PUT` | `/api/favorites` | Bearer | `{teamIds: [...]}` replaces the list (used to merge on first sign-in) |
| `PUT` | `/api/favorites/{teamId}` | Bearer | Add one team |
| `DELETE` | `/api/favorites/{teamId}` | Bearer | Remove one team |

Errors come back as `{"error": "message"}` with a matching status (400, 401, 409, 429), and the messages are written to be shown to users as-is.

## Data model

```
app_user                         user_session                     favorite_team
─────────────────────────        ──────────────────────────       ─────────────────────
id            bigint PK          token_hash  char(64) PK          user_id  → app_user
username      unique, nullable   user_id     → app_user           team_id  int (MLB id)
password_hash bcrypt, nullable   created_at                       created_at
google_subject unique, nullable  expires_at  (30 days)            PK (user_id, team_id)
email, display_name, theme
```

Deleting a user cascades to their sessions and favorites. Migrations live in [`src/main/resources/db/migration`](src/main/resources/db/migration).

## Security

- **Passwords** are hashed with bcrypt. Login takes the same time whether or not the username exists, so it doesn't reveal which usernames are registered.
- **Session tokens** are 256-bit random values. Only their SHA-256 hash is stored, so a copy of the database can't be used to sign in.
- **Brute-force protection:** 10 failed logins per username in 15 minutes, and 10 sign-ups per IP per hour.
- **Google sign-in:** each ID token is checked for signature (via Google), audience (this app's client id), issuer and expiry.
- **CORS** only allows the StatDugout site.
- **No secrets in the repo.** The database URL and Google client id come from environment variables, and the connection-string parser never puts the password in logs or error messages.

## Running locally

Requires Java 17. No database needed (it uses in-memory H2).

```bash
./mvnw spring-boot:run          # Windows: .\mvnw spring-boot:run
curl localhost:8080/api/health  # {"status":"ok","database":"H2","tablesReady":true}
```

To use Postgres instead, set `DATABASE_URL` to a connection string such as `postgresql://user:password@host/dbname?sslmode=require`.

## Tests

```bash
./mvnw test
```

- **Integration tests** (`AccountFlowTest`) run the real endpoints against an in-memory database: sign-up, sign-in, case-insensitive usernames, taken usernames, wrong passwords, favorites add/replace/remove, sign-out and missing tokens.
- **Unit tests** cover username/password rules, tokens, the rate limiter, Google token checks and parsing Neon's connection string.
- Tests always use H2 and never touch the production database, even if `DATABASE_URL` is set on your machine.

GitHub Actions runs the tests and builds the Docker image on every push.

## Deployment

Deployed on Render as a Docker web service ([`Dockerfile`](Dockerfile)): a two-stage build compiles with Maven, then runs on a slim Java runtime image as a non-root user.

| Environment variable | Where it comes from |
|---|---|
| `DATABASE_URL` | Neon → Connect, with connection pooling **off** (Flyway needs a direct connection) |
| `GOOGLE_CLIENT_ID` | Google Cloud → Google Auth Platform → Clients (web application) |
| `PORT` | Set by Render automatically |

## Project layout

```
src/main/java/com/baseballstats/baseballstats/
├── BaseballStatsApplication.java   entry point
├── account/                        sign-in, sessions, favorites
│   ├── AuthController.java         /api/auth/*, /api/me
│   ├── FavoritesController.java    /api/favorites
│   ├── AccountStore.java           all SQL for accounts, sessions and favorites
│   ├── GoogleTokenVerifier.java    checks Google ID tokens
│   ├── AccountRules.java           username/password rules
│   ├── AttemptLimiter.java         brute-force protection
│   ├── Tokens.java                 token generation and hashing
│   └── ApiException.java, ApiErrorHandler.java   {"error": ...} responses
├── config/
│   ├── DatabaseConfig.java         Neon connection pool (or H2 when DATABASE_URL isn't set)
│   ├── PostgresUrl.java            turns Neon's URL into a JDBC URL
│   └── CorsConfig.java             which sites may call the API
└── health/HealthController.java    /api/ping, /api/health
```

---

Built by Zachary Sheppard.
