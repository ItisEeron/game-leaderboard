# Game Leaderboard

A Spring Boot REST API for managing games and their leaderboards.

## Requirements

- Java 21
- Maven (or use the included `mvn` if already installed)

## Running locally

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

To run with Docker instead:

```bash
docker build -t game-leaderboard .
docker run -p 8080:8080 game-leaderboard
```

## Quick Start

```bash
# 1. Create a game
curl -X POST localhost:8080/api/games -H "Content-Type: application/json" -d '{"name":"Chess"}'
# => {"id":1,"name":"Chess"}

# 2. Submit a score
curl -X POST localhost:8080/api/leaderboard -H "Content-Type: application/json" \
  -d '{"gameId":1,"playerName":"alice","score":90}'
# => {"id":1,"gameId":1,"playerName":"alice","score":90,"submittedAt":"..."}

# 3. See the ranked leaderboard for that game
curl "localhost:8080/api/leaderboard/game/1?size=10"

# 4. See one entry's rank and its neighbors
curl localhost:8080/api/leaderboard/1/rank
```

See the API Reference below for the full endpoint list, and Idempotency/Error Handling for how retries and failures behave.

## Running tests

```bash
mvn test
```

Includes unit tests for the controllers/exception handling, and integration tests (`src/test/java/.../integration/`) that exercise the full HTTP stack: validation and not-found errors, simulated database outages and unknown failures, concurrency races (simultaneous submissions, and a get/delete race on the same entry), and idempotency (duplicate-request replay, concurrent duplicates, and that a failed request doesn't poison its key).

## Storage

`GameRepository` and `LeaderboardRepository` are interfaces with two implementations, selected automatically at startup by `com.example.leaderboard.config.RepositoryConfig`:

- **In-memory** (default) — backed by `ConcurrentHashMap`, data resets on every restart. Used whenever `spring.datasource.url` is not set.
- **Database** (JPA/H2) — used automatically once `spring.datasource.url` is set. See `src/main/resources/application.properties` for the commented-out settings to enable a file-persisted H2 database. Swapping to Postgres/MySQL later is a matter of changing the driver/URL, not the code.

### Known tradeoffs (beta)

- **App-generated IDs**: both implementations assign ids in application code (an `AtomicLong`, seeded from the current max id in the database on startup for the JPA path) rather than using the database's native auto-increment. This keeps the two backends behaviorally identical for now, but it's a stopgap — it doesn't handle multiple app instances writing to the same database concurrently. Once we're past beta and standardize on the database backend, switch `Game`/`LeaderboardEntry` ids to `@GeneratedValue` and drop the in-repository id generators.
- **Idle datasource when no database is configured**: because the H2 driver is on the classpath, Spring Boot still auto-configures its own (unused, ephemeral) embedded `DataSource`/connection pool at startup even when `spring.datasource.url` is unset — it's just never used, since `RepositoryConfig` picks the in-memory beans in that case. Harmless, but adds a bit of startup overhead.

## API Reference

### Games

| Method | Path              | Description               |
|--------|-------------------|---------------------------|
| GET    | `/api/games`      | List all games            |
| GET    | `/api/games/{id}` | Get a game by id          |
| POST   | `/api/games`      | Create a game             |
| DELETE | `/api/games/{id}` | Delete a game by id       |

**Create a game**

```bash
curl -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -d '{"name":"Chess"}'
```

Response:

```json
{"id":1,"name":"Chess"}
```

### Leaderboard

| Method | Path                            | Description                                                      |
|--------|----------------------------------|-------------------------------------------------------------------|
| GET    | `/api/leaderboard?page={n}&size={n}`      | List all leaderboard entries, paginated (default size 100)              |
| GET    | `/api/leaderboard/{id}`          | Get a single leaderboard entry by id                              |
| GET    | `/api/leaderboard/{id}/rank`     | A player's rank for their game, plus up to 10 entries immediately above and below them |
| GET    | `/api/leaderboard/game/{gameId}?page={n}&size={n}` | Ranked entries for one game (highest score first, ties broken by earliest submission), paginated (default size 100) |
| POST   | `/api/leaderboard`               | Create a leaderboard entry                                        |
| DELETE | `/api/leaderboard/{id}`          | Delete a leaderboard entry by id                                  |

**Create a leaderboard entry**

```bash
curl -X POST http://localhost:8080/api/leaderboard \
  -H "Content-Type: application/json" \
  -d '{"gameId":1,"playerName":"alice","score":90}'
```

Response:

```json
{"id":1,"gameId":1,"playerName":"alice","score":90,"submittedAt":"2026-09-15T21:21:07.918343949Z"}
```

**Get ranked leaderboard for a game**

```bash
curl http://localhost:8080/api/leaderboard/game/1
```

Response:

```json
{
  "content": [
    {"id":1,"gameId":1,"playerName":"alice","score":90,"submittedAt":"2026-09-15T21:21:07.918343949Z"}
  ],
  "page": 0,
  "size": 100,
  "totalElements": 1
}
```

Requesting a `gameId` that doesn't exist returns `404 Not Found`.

**Get a leaderboard page of a given size**

`size` controls how many results come back per page (default 100) — set it to any positive number, e.g. top 10, or as many as 200:

```bash
curl "http://localhost:8080/api/leaderboard/game/1?size=10"
```

`page` and `size` both work the same way on `/api/leaderboard` and `/api/leaderboard/game/{gameId}`. `size` must be at least 1 and `page` must not be negative; violating either returns `400 Bad Request`.

**Get a player's rank and surrounding context**

Returns the entry's rank within its game's leaderboard, plus up to 10 entries immediately above it and 10 immediately below (fewer if the entry is near the top or bottom):

```bash
curl http://localhost:8080/api/leaderboard/2/rank
```

Response:

```json
{
  "rank": 2,
  "totalElements": 3,
  "entries": [
    {"id":1,"gameId":1,"playerName":"alice","score":90,"submittedAt":"2026-09-15T21:21:07.918343949Z"},
    {"id":2,"gameId":1,"playerName":"bob","score":70,"submittedAt":"2026-09-15T21:22:01.402219482Z"},
    {"id":3,"gameId":1,"playerName":"carol","score":50,"submittedAt":"2026-09-15T21:23:44.918343949Z"}
  ]
}
```

Requesting a leaderboard entry id that doesn't exist returns `404 Not Found`.

**Delete a game or leaderboard entry**

```bash
curl -X DELETE http://localhost:8080/api/games/1
curl -X DELETE http://localhost:8080/api/leaderboard/1
```

Both return `204 No Content` on success, `404 Not Found` if the id doesn't exist.

## Idempotency

Any `POST` request (creating a game or a leaderboard entry) can carry an `Idempotency-Key` header. Retrying the same request with the same key returns the original response (with an `Idempotent-Replay: true` header) instead of creating a duplicate — useful when a client can't tell whether a prior request actually succeeded (e.g. after a dropped connection):

```bash
curl -X POST http://localhost:8080/api/leaderboard \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 3f1a2b4c-unique-per-logical-request" \
  -d '{"gameId":1,"playerName":"alice","score":90}'
```

Notes:

- The key is scoped per endpoint path, so reusing the same key value on `/api/games` and `/api/leaderboard` won't collide.
- Only successful (2xx) responses are cached. A request that fails (e.g. validation error, unknown `gameId`) isn't cached against that key, so you can fix the problem and retry with the same key.
- Concurrent requests carrying the same key are serialized — the second waits for the first to finish and replays its result, rather than both creating separate entries.
- Omitting the header disables idempotency entirely for that request, preserving the original behavior (a duplicate request creates a duplicate entry).
- The dedup store is in-memory and per app instance — see the architecture doc's Known Tradeoffs for what that means once this runs behind multiple instances.

## Error Handling

Every error response is a JSON body of this shape, produced by `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-09-15T21:46:50.442384797Z",
  "status": 400,
  "error": "Bad Request",
  "message": "name must not be blank"
}
```

| Status | When |
|--------|------|
| `400 Bad Request` | A request body fails validation (e.g. blank `name`/`playerName`, missing `gameId`), `page`/`size` query params are invalid, the JSON body is malformed, or a path variable has the wrong type (e.g. `/api/games/not-a-number`) |
| `404 Not Found` | The referenced game or leaderboard entry doesn't exist |
| `503 Service Unavailable` | The database is unreachable or otherwise failing (connection refused, pool exhausted, timeout) — the failure is on the DB's end, not the caller's, so the client can typically retry |
| `500 Internal Server Error` | A fallback for anything else unhandled, so clients never see a raw stack trace |

For `503`/`500`, the real exception is logged server-side but never included in the response body.
