package com.example.leaderboard.repository.jpa;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.repository.LeaderboardRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database-backed implementation, used when a datasource is configured; see
 * {@link com.example.leaderboard.config.RepositoryConfig}.
 */
public class JpaLeaderboardRepository implements LeaderboardRepository {

    private final LeaderboardJpaRepository jpaRepository;
    private final AtomicLong idGenerator;

    public JpaLeaderboardRepository(LeaderboardJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.idGenerator = new AtomicLong(jpaRepository.findMaxId());
    }

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score) {
        return save(gameId, playerName, score, Instant.now());
    }

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score, Instant submittedAt) {
        long id = idGenerator.incrementAndGet();
        return jpaRepository.save(new LeaderboardEntry(id, gameId, playerName, score, submittedAt));
    }

    @Override
    public Optional<LeaderboardEntry> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<LeaderboardEntry> deleteById(Long id) {
        Optional<LeaderboardEntry> existing = jpaRepository.findById(id);
        existing.ifPresent(jpaRepository::delete);
        return existing;
    }

    @Override
    public Collection<LeaderboardEntry> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<LeaderboardEntry> findByGameIdRanked(Long gameId) {
        return jpaRepository.findByGameIdOrderByScoreDescSubmittedAtAsc(gameId);
    }
}
