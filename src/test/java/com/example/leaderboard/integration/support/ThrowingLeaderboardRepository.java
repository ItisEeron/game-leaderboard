package com.example.leaderboard.integration.support;

import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.repository.LeaderboardRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Test double that always fails, used to drive requests through the real HTTP stack
 * and confirm GlobalExceptionHandler maps a given failure to the right response.
 */
public class ThrowingLeaderboardRepository implements LeaderboardRepository {

    private final Supplier<? extends RuntimeException> failure;

    public ThrowingLeaderboardRepository(Supplier<? extends RuntimeException> failure) {
        this.failure = failure;
    }

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score) {
        throw failure.get();
    }

    @Override
    public LeaderboardEntry save(Long gameId, String playerName, long score, Instant submittedAt) {
        throw failure.get();
    }

    @Override
    public Optional<LeaderboardEntry> findById(Long id) {
        throw failure.get();
    }

    @Override
    public Optional<LeaderboardEntry> deleteById(Long id) {
        throw failure.get();
    }

    @Override
    public Collection<LeaderboardEntry> findAll() {
        throw failure.get();
    }

    @Override
    public List<LeaderboardEntry> findByGameIdRanked(Long gameId) {
        throw failure.get();
    }
}
