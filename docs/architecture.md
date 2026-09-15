---
title: Game Leaderboard — Architecture
---

# Game Leaderboard — Architecture

## Overview Diagram

```
                                     +------------+
                     live push       |   Client    |
                (WebSocket/SSE, -----+-----+------+
                 optional)     |           |
                                |  +--------v----------+
                                |  | Global Load Balancer|  (routes to nearest region)
                                |  +---------+----------+
                                |  +----------+----------+
                                |  |                     |
                                | +v------------+  +-----v-------+
                                | | Region: API  |  | Region: API |   (stateless,
                                | | (Leaderboard |  | (Leaderboard |    horizontally
                                | |  Service)    |  |  Service)    |    scaled)
                                | +-+---------+--+  +-+---------+-+
                                |   | writes   | reads |          | reads
                                |   |          +-------+----------+
                                |   |                  v
                                |   |          +-----------------------------+
                                |   |          |  Regional Read Cache / ZSET  |  (per-game sorted set,
                                |   |          |  e.g. Redis, top-100 + rank  |   eventually consistent)
                                |   |          +--------------^--------------+
                                |   |                          | fan-out (async)
                                |   v                          |
                                | +----------------------+     |
                                | |  Primary Write Store   |----+
                                | |  (single-leader DB,     |
                                | |   strongly consistent,  |
                                | |   idempotency-key table) |
                                | +-----------+----------+
                                |             |
                                |             v
                                |     +----------------+
                                +-----|  Event Bus /    |
                                      |  Queue           |
                                      |  (Kafka topic)   |
                                      +---+----------+---+
                                          |          |
                                          v          v
                              (fan-out to cache)  (optional consumer:
                                                    Real-Time Push Service
                                                    for live competition mode)
```

**Write path:** client -> nearest region -> primary write store (single leader, strong consistency) -> event published to the Kafka topic -> fanned out asynchronously to every region's read cache.

**Read path:** client -> nearest region -> served entirely from that region's local cache (no cross-region hop), so GETs stay fast globally even though they may lag the write by a bit.

**Live/competition path (future, optional):** a second consumer of the same Kafka topic — a Real-Time Push Service — streams score updates directly to clients watching a specific competition over WebSocket/SSE, without those clients needing to poll `GET` endpoints. See "Under Consideration: Real-Time Competition Updates" below.

## Example: Write and Read Flow

Concretely, submitting and then reading a score walks the diagram like this:

1. **Write** — `POST /api/leaderboard {"gameId":1,"playerName":"alice","score":90}` lands on the nearest region's API, which writes it to the single-leader primary store (deduped on `Idempotency-Key` if the client sent one) and returns `201` immediately. The primary store then publishes a "score updated" event to the Kafka topic.
2. **Fan-out** — every region's cache consumer picks up that event and updates its local sorted set for `gameId=1`, asynchronously and independently of the write's response.
3. **Read** — `GET /api/leaderboard/game/1` and `GET /api/leaderboard/{id}/rank`, from any region, are served entirely from that region's local sorted set — no hop back to the primary store — so they stay fast even under global load. They may briefly lag the write until fan-out completes.

## Functional Requirements

1. Users can post score updates for specific games.
2. Query the top 100 for a game via a `GET` request.
3. Query a specific `userId`'s score and rank for a specific `gameId` — returns both the user's raw score and their rank relative to other players.

## Non-Functional Requirements and Design Decisions

| Requirement | How the architecture addresses it |
|---|---|
| Global scale, GETs return expected data | Reads are served from per-region caches (sorted sets), not the primary DB — horizontally scalable, low-latency everywhere |
| Writes favor consistency over availability | All writes go through a single-leader primary store; no multi-master write conflicts |
| Writes aren't latency-critical; availability/fan-out matter more | Writes commit to the primary, then propagate to regional caches asynchronously via an event bus — decouples write latency from global read visibility |
| Updates must be idempotent | Each write can carry an `Idempotency-Key` header; the service dedupes on that key (namespaced per endpoint) before applying, so retries are safe |

## API Reference

### Games

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/games` | Lists every game. |
| `GET` | `/api/games/{id}` | Fetches one game by id; `404` if it doesn't exist. |
| `POST` | `/api/games` | Creates a game from a JSON body (`{"name": "Chess"}`); returns the created game with its assigned id. |
| `DELETE` | `/api/games/{id}` | Deletes a game by id; `204` on success, `404` if it doesn't exist. |

### Leaderboard

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/leaderboard?page={n}&size={n}` | Lists all leaderboard entries across every game, paginated (default page size 100). |
| `GET` | `/api/leaderboard/{id}` | Fetches a single leaderboard entry by id; `404` if it doesn't exist. |
| `GET` | `/api/leaderboard/{id}/rank` | Returns that entry's rank within its game, its `totalElements`, and up to 10 entries immediately above and below it. |
| `GET` | `/api/leaderboard/game/{gameId}?page={n}&size={n}` | Returns a game's leaderboard ranked highest score first (ties broken by earliest submission), paginated. |
| `POST` | `/api/leaderboard` | Submits a score (`{"gameId": 1, "playerName": "alice", "score": 90}`); returns the created entry, including a server-assigned `submittedAt` timestamp. `404` if `gameId` doesn't exist. |
| `DELETE` | `/api/leaderboard/{id}` | Deletes a leaderboard entry by id; `204` on success, `404` if it doesn't exist. |

All error responses share one JSON shape (`timestamp`, `status`, `error`, `message`); see the README's Error Handling section for the full status code table.

## Known Tradeoffs (current implementation vs. target architecture)

The service as built today is a single-region, in-memory prototype. It proves out the API shape and business logic, but several pieces described above haven't been built yet:

- **Storage: in-memory instead of SQL.** We're currently using `ConcurrentHashMap`-backed repositories rather than a real SQL data store, purely to move faster during initial development. A JPA-backed implementation already exists behind the same repository interfaces (see the README's Storage section), so switching to Postgres/MySQL is a configuration change, not a rewrite — but until that switch happens, we don't have real durability or the single-leader consistency guarantees the target design calls for.
- **No cache layer yet.** The target architecture calls for a Redis-backed sorted set per game for fast top-N and rank reads. Right now every read is served directly from the repository (in-memory map or, once switched over, the SQL store) with no caching in front of it. This is fine at small scale, but won't hold up under the global read volume the non-functional requirements describe.
- **ID generation.** We're using an in-memory `AtomicLong` per repository to assign ids. A SQL data store can manage id assignment natively (auto-increment / sequences), which would be the better long-term approach — but for now, the app-generated counter is sufficient and keeps the in-memory and database-backed repositories behaviorally identical.
- **Idempotency keys are implemented, but only per-instance.** A client can send an `Idempotency-Key` header on any `POST` (`/api/games` or `/api/leaderboard`); `IdempotencyFilter` dedupes on `path + key` and replays the original response for a repeat, including for genuinely concurrent duplicate requests (the second waits for the first rather than racing it). Only successful (2xx) responses are cached, so a request that failed validation isn't permanently stuck on that key — the caller can retry it after fixing the problem. The gap versus the target architecture: the dedup table is an in-memory `ConcurrentHashMap`, not the primary store, so it only catches duplicates within a single app instance. Once this service runs behind a load balancer with multiple instances (per the target architecture's regional API layer), the same idempotency key sent to two different instances would not be deduped — that requires moving the dedup table into the primary write store itself, as the target design originally called for.
- **No load balancer yet.** The diagram's "route to nearest region" / multiple stateless API instances behind a global load balancer doesn't exist yet — the service currently runs as a single instance. Scaling out to multiple instances/regions depends on this being added.

## Under Consideration: Dedicated Write Queue

Since writes are explicitly not latency-critical (per the non-functional requirements) while reads must stay fast globally, a dedicated write queue is worth adding once we move past the current prototype:

- **Shape:** incoming score submissions land in a durable, persistent queue (e.g. Kafka or SQS — not an in-app/in-memory buffer, since that would lose buffered writes on a crash) rather than writing straight to the primary store.
- **Batching:** a consumer flushes the queue as batch inserts to the primary store on a timer or size threshold, instead of one write per request. This trades a small amount of write-visibility latency for much lower write load on the primary store — an acceptable tradeoff given writes aren't urgent.
- **Idempotency fits here too:** the dedupe check (idempotency key) should happen at enqueue time, before a submission ever reaches a batch, so retries are caught early rather than relying on the batch/DB layer to catch them.
- **Reads stay unaffected:** the read path never touches this queue — it's served entirely from the regional cache — so batching writes doesn't add latency to `GET` requests, which matches the priority on fast reads.

## Under Consideration: Real-Time Competition Updates

Batched, cache-served reads are the right default for a general leaderboard — but for a "watch a competition live" feature, someone's score change should be visible immediately, not on the next cache refresh. This doesn't require replacing the batching approach above; it can layer on top of it:

- **A second consumer on the same Kafka topic.** The write queue already publishes an event per score update. A separate Real-Time Push Service subscribes to that same topic (independently of the batch-insert consumer) and pushes updates out over WebSocket/SSE to clients currently watching a given game or competition.
- **Scoped, not global.** This only needs to push to clients actively subscribed to a specific game/competition — not broadcast every score update everywhere — so it stays cheap even at scale.
- **Doesn't compromise the read-priority design.** The cache-backed `GET` endpoints stay as the default, low-cost path for everyone else; live push is an opt-in feature for whoever's actively watching a competition, not a replacement for the general read path.
- **Not yet implemented.** This is a future capability to design for, not something the current service does — called out here so the option is preserved (e.g. by keeping write events flowing through a single topic that can gain additional consumers later) rather than designed away.
