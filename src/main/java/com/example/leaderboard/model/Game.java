package com.example.leaderboard.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Also mapped as a JPA entity so it can be persisted by the database-backed repository.
 * The id is application-assigned (not {@code @GeneratedValue}) to match the in-memory
 * repository's id scheme; see README for the tradeoffs of that choice.
 */
@Entity
@Getter
public class Game {

    @Id
    private final Long id;
    @Setter
    @NotBlank(message = "name must not be blank")
    private String name;

    public Game() {
        this.id = null;
    }

    public Game(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
