package com.example.leaderboard.repository;

import com.example.leaderboard.model.Game;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GameRepository {

    private final Map<Long, Game> games = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    public Game save(String name) {
        long id = idGenerator.incrementAndGet();
        Game game = new Game(id, name);
        games.put(id, game);
        return game;
    }

    public Optional<Game> findById(Long id) {
        return Optional.ofNullable(games.get(id));
    }

    public boolean existsById(Long id) {
        return id != null && games.containsKey(id);
    }

    public Collection<Game> findAll() {
        return games.values();
    }

    public Optional<Game> deleteById(Long id) {
        return Optional.ofNullable(games.remove(id));
    }
}
