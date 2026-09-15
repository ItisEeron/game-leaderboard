package com.example.leaderboard.repository.jpa;

import com.example.leaderboard.model.Game;
import com.example.leaderboard.repository.GameRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Database-backed implementation, used when a datasource is configured; see
 * {@link com.example.leaderboard.config.RepositoryConfig}.
 */
public class JpaGameRepository implements GameRepository {

    private final GameJpaRepository jpaRepository;
    private final AtomicLong idGenerator;

    public JpaGameRepository(GameJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.idGenerator = new AtomicLong(jpaRepository.findMaxId());
    }

    @Override
    public Game save(String name) {
        long id = idGenerator.incrementAndGet();
        return jpaRepository.save(new Game(id, name));
    }

    @Override
    public Optional<Game> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && jpaRepository.existsById(id);
    }

    @Override
    public Collection<Game> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Optional<Game> deleteById(Long id) {
        Optional<Game> existing = jpaRepository.findById(id);
        existing.ifPresent(jpaRepository::delete);
        return existing;
    }
}
