package com.example.leaderboard.repository.memory;

import com.example.leaderboard.model.Game;
import com.example.leaderboard.repository.GameRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default backend, used when no database is configured. Bean registration/selection
 * happens in {@link com.example.leaderboard.config.RepositoryConfig}.
 */
public class InMemoryGameRepository implements GameRepository {

    private final Map<Long, Game> games = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong();

    @Override
    public Game save(String name) {
        long id = idGenerator.incrementAndGet();
        Game game = new Game(id, name);
        games.put(id, game);
        return game;
    }

    @Override
    public Optional<Game> findById(Long id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && games.containsKey(id);
    }

    @Override
    public Collection<Game> findAll() {
        return games.values();
    }

    @Override
    public Optional<Game> deleteById(Long id) {
        return Optional.ofNullable(games.remove(id));
    }
}
