package com.example.leaderboard.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void gameNotFound_mapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleGameNotFound(new GameNotFoundException(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Game not found: 1");
    }

    @Test
    void leaderboardEntryNotFound_mapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleLeaderboardEntryNotFound(new LeaderboardEntryNotFoundException(1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void databaseFailure_mapsTo503_notLeakingInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleDataAccessFailure(
                new DataAccessResourceFailureException("Connection refused to db-host:5432"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().message()).doesNotContain("db-host");
    }

    @Test
    void unhandledException_mapsTo500_notLeakingInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnknown(new IllegalStateException("boom: secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).doesNotContain("secret detail");
    }
}
