package com.example.leaderboard.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Also mapped as a JPA entity so it can be persisted by the database-backed repository.
 * The id is application-assigned (not {@code @GeneratedValue}) to match the in-memory
 * repository's id scheme; see README for the tradeoffs of that choice.
 */
@Entity
@Getter
public class LeaderboardEntry {

    @Id
    private final Long id;
    @Setter
    @NotNull(message = "gameId must not be null")
    private Long gameId;
    @Setter
    @NotBlank(message = "playerName must not be blank")
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
