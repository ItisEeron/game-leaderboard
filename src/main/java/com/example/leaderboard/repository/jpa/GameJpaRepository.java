package com.example.leaderboard.repository.jpa;

import com.example.leaderboard.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameJpaRepository extends JpaRepository<Game, Long> {

    @Query("SELECT COALESCE(MAX(g.id), 0) FROM Game g")
    long findMaxId();
}
