package com.example.leaderboard.model;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Game {

    private final Long id;
    @Setter
    private String name;

    public Game() {
        this.id = null;
    }

    public Game(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
