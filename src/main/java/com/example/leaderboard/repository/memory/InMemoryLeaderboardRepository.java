package com.example.leaderboard.repository.memory;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.repository.LeaderboardRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default backend, used when no database is configured. Bean registration/selection
 * happens in {@link com.example.leaderboard.config.RepositoryConfig}.
 */
public class InMemoryLeaderboardRepository implements LeaderboardRepository {

    private final Map<Long, LeaderboardEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score) {
        return save(gameId, playerName, score, Instant.now());
    }

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score, Instant submittedAt) {
        long id = idGenerator.incrementAndGet();
        LeaderboardEntry entry = new LeaderboardEntry(id, gameId, playerName, score, submittedAt);
        entries.put(id, entry);
        return entry;
    }

    @Override
    public Optional<LeaderboardEntry> findById(Long id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public Optional<LeaderboardEntry> deleteById(Long id) {
        return Optional.ofNullable(entries.remove(id));
    }

    @Override
    public Collection<LeaderboardEntry> findAll() {
        return entries.values();
    }

    @Override
    public List<LeaderboardEntry> findByGameIdRanked(Long gameId) {
        return entries.values().stream()
                .filter(e -> gameId.equals(e.getGameId()))
                .sorted(Comparator.comparingLong(LeaderboardEntry::getScore).reversed()
                        .thenComparing(LeaderboardEntry::getSubmittedAt))
                .collect(Collectors.toList());
    }
}
