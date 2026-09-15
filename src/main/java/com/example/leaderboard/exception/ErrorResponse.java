package com.example.leaderboard.exception;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message) {

    public ErrorResponse(int status, String error, String message) {
        this(Instant.now(), status, error, message);
    }
}
