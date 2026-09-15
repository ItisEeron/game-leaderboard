package com.example.leaderboard.config;

import com.example.leaderboard.repository.GameRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import com.example.leaderboard.repository.jpa.GameJpaRepository;
import com.example.leaderboard.repository.jpa.JpaGameRepository;
import com.example.leaderboard.repository.jpa.JpaLeaderboardRepository;
import com.example.leaderboard.repository.jpa.LeaderboardJpaRepository;
import com.example.leaderboard.repository.memory.InMemoryGameRepository;
import com.example.leaderboard.repository.memory.InMemoryLeaderboardRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the storage backend for the two repositories: a database, if one is configured
 * via {@code spring.datasource.url}, otherwise the in-memory fallback. The JPA beans are
 * declared first in each pair so their {@code @ConditionalOnMissingBean} sibling can see
 * whether one was already registered.
 */
@Configuration
public class RepositoryConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    public GameRepository jpaGameRepository(GameJpaRepository jpaRepository) {
        return new JpaGameRepository(jpaRepository);
    }

    @Bean
    @ConditionalOnMissingBean(GameRepository.class)
    public GameRepository inMemoryGameRepository() {
        return new InMemoryGameRepository();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    public LeaderboardRepository jpaLeaderboardRepository(LeaderboardJpaRepository jpaRepository) {
        return new JpaLeaderboardRepository(jpaRepository);
    }

    @Bean
    @ConditionalOnMissingBean(LeaderboardRepository.class)
    public LeaderboardRepository inMemoryLeaderboardRepository() {
        return new InMemoryLeaderboardRepository();
    }
}
