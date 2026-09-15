package com.example.leaderboard.repository;

import com.example.leaderboard.model.LeaderboardEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class LeaderboardRepository {

    private final Map<Long, LeaderboardEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    public LeaderboardEntry save(Long gameId, String playerName, long score) {
        return save(gameId, playerName, score, Instant.now());
    }

    public LeaderboardEntry save(Long gameId, String playerName, long score, Instant submittedAt) {
        long id = idGenerator.incrementAndGet();
        LeaderboardEntry entry = new LeaderboardEntry(id, gameId, playerName, score, submittedAt);
        entries.put(id, entry);
        return entry;
    }

    public Optional<LeaderboardEntry> findById(Long id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Optional<LeaderboardEntry> deleteById(Long id) {
        return Optional.ofNullable(entries.remove(id));
    }

    public Collection<LeaderboardEntry> findAll() {
        return entries.values();
    }

    /**
     * Entries for a game, ranked highest score first; ties broken by whoever submitted earliest.
     */
    public List<LeaderboardEntry> findByGameIdRanked(Long gameId) {
        return entries.values().stream()
                .filter(e -> gameId.equals(e.getGameId()))
                .sorted(Comparator.comparingLong(LeaderboardEntry::getScore).reversed()
                        .thenComparing(LeaderboardEntry::getSubmittedAt))
                .collect(Collectors.toList());
    }
}
