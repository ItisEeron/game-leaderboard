package com.example.leaderboard.model;

import lombok.Getter;

import java.util.List;

/**
 * A player's rank within a game's leaderboard, plus the entries immediately
 * surrounding them (highest score first, same order as the ranked leaderboard).
 */
@Getter
public class LeaderboardContext {

    private final long rank;
    private final long totalElements;
    private final List<LeaderboardEntry> entries;

    public LeaderboardContext(long rank, long totalElements, List<LeaderboardEntry> entries) {
        this.rank = rank;
        this.totalElements = totalElements;
        this.entries = entries;
    }
}
