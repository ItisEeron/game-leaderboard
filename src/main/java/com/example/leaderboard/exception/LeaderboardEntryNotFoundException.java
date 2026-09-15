package com.example.leaderboard.exception;

/**
 * Status/body are set by {@link GlobalExceptionHandler}.
 */
public class LeaderboardEntryNotFoundException extends RuntimeException {

    public LeaderboardEntryNotFoundException(Long id) {
        super("Leaderboard entry not found: " + id);
    }
}
