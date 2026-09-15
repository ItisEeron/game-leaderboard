package com.example.leaderboard.exception;

/**
 * Status/body are set by {@link GlobalExceptionHandler}.
 */
public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(Long gameId) {
        super("Game not found: " + gameId);
    }
}
