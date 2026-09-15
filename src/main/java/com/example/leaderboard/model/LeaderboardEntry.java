package com.example.leaderboard.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
public class LeaderboardEntry {

    private final Long id;
    @Setter
    private Long gameId;
    @Setter
    private String playerName;
    @Setter
    private long score;
    private final Instant submittedAt;

    public LeaderboardEntry() {
        this.id = null;
        this.submittedAt = null;
    }

    public LeaderboardEntry(Long id, Long gameId, String playerName, long score, Instant submittedAt) {
        this.id = id;
        this.gameId = gameId;
        this.playerName = playerName;
        this.score = score;
        this.submittedAt = submittedAt;
    }
}
