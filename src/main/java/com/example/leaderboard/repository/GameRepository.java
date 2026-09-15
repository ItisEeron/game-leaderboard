package com.example.leaderboard.repository;

import com.example.leaderboard.model.Game;

import java.util.Collection;
import java.util.Optional;

public interface GameRepository {

    Game save(String name);

    Optional<Game> findById(Long id);

    boolean existsById(Long id);

    Collection<Game> findAll();

    Optional<Game> deleteById(Long id);
}
