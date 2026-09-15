package com.example.leaderboard.repository.jpa;

import com.example.leaderboard.model.LeaderboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeaderboardJpaRepository extends JpaRepository<LeaderboardEntry, Long> {

    List<LeaderboardEntry> findByGameIdOrderByScoreDescSubmittedAtAsc(Long gameId);

    @Query("SELECT COALESCE(MAX(e.id), 0) FROM LeaderboardEntry e")
    long findMaxId();
}
