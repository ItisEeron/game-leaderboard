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

## Running tests

```bash
mvn test
```

## Storage

Data is currently stored in-memory (`ConcurrentHashMap`) and resets on every restart. This will be replaced with a persistent database later.

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
| GET    | `/api/leaderboard?page={n}`      | List all leaderboard entries, paginated 100 per page              |
| GET    | `/api/leaderboard/{id}`          | Get a single leaderboard entry by id                              |
| GET    | `/api/leaderboard/game/{gameId}?page={n}` | Ranked entries for one game (highest score first, ties broken by earliest submission), paginated 100 per page |
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

**Delete a game or leaderboard entry**

```bash
curl -X DELETE http://localhost:8080/api/games/1
curl -X DELETE http://localhost:8080/api/leaderboard/1
```

Both return `204 No Content` on success, `404 Not Found` if the id doesn't exist.
