package com.example.leaderboard.repository;

import com.example.leaderboard.model.LeaderboardEntry;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaderboardRepository {

    LeaderboardEntry save(Long gameId, String playerName, long score);

    LeaderboardEntry save(Long gameId, String playerName, long score, Instant submittedAt);

    Optional<LeaderboardEntry> findById(Long id);

    Optional<LeaderboardEntry> deleteById(Long id);

    Collection<LeaderboardEntry> findAll();

    /**
     * Entries for a game, ranked highest score first; ties broken by whoever submitted earliest.
     */
    List<LeaderboardEntry> findByGameIdRanked(Long gameId);
}
