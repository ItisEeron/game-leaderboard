package com.example.leaderboard.controller;

import com.example.leaderboard.exception.GameNotFoundException;
import com.example.leaderboard.model.Game;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.PageResponse;
import com.example.leaderboard.repository.GameRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardControllerTest {

    private GameRepository gameRepository;
    private LeaderboardRepository leaderboardRepository;
    private LeaderboardController controller;

    @BeforeEach
    void setUp() {
        gameRepository = new GameRepository();
        leaderboardRepository = new LeaderboardRepository();
        controller = new LeaderboardController(leaderboardRepository, gameRepository);
    }

    @Test
    void getByGame_nonExistentGame_throwsGameNotFoundException() {
        assertThrows(GameNotFoundException.class, () -> controller.getByGame(999L, 0));
    }

    @Test
    void getByGame_fewerThan100Scores_returnsSortedDescendingByScore() {
        Game game = gameRepository.save("Chess");

        leaderboardRepository.save(game.getId(), "alice", 50);
        leaderboardRepository.save(game.getId(), "bob", 90);
        leaderboardRepository.save(game.getId(), "carol", 70);

        PageResponse<LeaderboardEntry> page = controller.getByGame(game.getId(), 0);

        assertThat(page.getContent()).extracting(LeaderboardEntry::getScore)
                .containsExactly(90L, 70L, 50L);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getByGame_tiedScores_earlierSubmissionRanksHigher() {
        Game game = gameRepository.save("Chess");
        Instant earlier = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant later = Instant.now();

        LeaderboardEntry secondSubmitted = leaderboardRepository.save(game.getId(), "bob", 100, later);
        LeaderboardEntry firstSubmitted = leaderboardRepository.save(game.getId(), "alice", 100, earlier);

        PageResponse<LeaderboardEntry> page = controller.getByGame(game.getId(), 0);

        assertThat(page.getContent()).extracting(LeaderboardEntry::getPlayerName)
                .containsExactly(firstSubmitted.getPlayerName(), secondSubmitted.getPlayerName());
    }

    @Test
    void getAll_paginatesInGroupsOf100() {
        Game game = gameRepository.save("Chess");
        for (int i = 0; i < 150; i++) {
            leaderboardRepository.save(game.getId(), "player" + i, i);
        }

        PageResponse<LeaderboardEntry> firstPage = controller.getAll(0);
        PageResponse<LeaderboardEntry> secondPage = controller.getAll(1);

        assertThat(firstPage.getContent()).hasSize(100);
        assertThat(secondPage.getContent()).hasSize(50);
        assertThat(firstPage.getTotalElements()).isEqualTo(150);
    }
}
