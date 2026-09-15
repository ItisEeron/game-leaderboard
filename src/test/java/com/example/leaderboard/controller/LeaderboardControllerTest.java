package com.example.leaderboard.controller;

import com.example.leaderboard.exception.GameNotFoundException;
import com.example.leaderboard.exception.LeaderboardEntryNotFoundException;
import com.example.leaderboard.model.Game;
import com.example.leaderboard.model.LeaderboardContext;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.PageResponse;
import com.example.leaderboard.repository.GameRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import com.example.leaderboard.repository.memory.InMemoryGameRepository;
import com.example.leaderboard.repository.memory.InMemoryLeaderboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderboardControllerTest {

    private GameRepository gameRepository;
    private LeaderboardRepository leaderboardRepository;
    private LeaderboardController controller;

    @BeforeEach
    void setUp() {
        gameRepository = new InMemoryGameRepository();
        leaderboardRepository = new InMemoryLeaderboardRepository();
        controller = new LeaderboardController(leaderboardRepository, gameRepository);
    }

    private static final int DEFAULT_PAGE_SIZE = LeaderboardController.DEFAULT_PAGE_SIZE;

    @Test
    void getByGame_nonExistentGame_throwsGameNotFoundException() {
        assertThrows(GameNotFoundException.class, () -> controller.getByGame(999L, 0, DEFAULT_PAGE_SIZE));
    }

    @Test
    void getByGame_fewerThan100Scores_returnsSortedDescendingByScore() {
        Game game = gameRepository.save("Chess");

        leaderboardRepository.save(game.getId(), "alice", 50);
        leaderboardRepository.save(game.getId(), "bob", 90);
        leaderboardRepository.save(game.getId(), "carol", 70);

        PageResponse<LeaderboardEntry> page = controller.getByGame(game.getId(), 0, DEFAULT_PAGE_SIZE);

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

        PageResponse<LeaderboardEntry> page = controller.getByGame(game.getId(), 0, DEFAULT_PAGE_SIZE);

        assertThat(page.getContent()).extracting(LeaderboardEntry::getPlayerName)
                .containsExactly(firstSubmitted.getPlayerName(), secondSubmitted.getPlayerName());
    }

    @Test
    void getAll_paginatesInGroupsOf100ByDefault() {
        Game game = gameRepository.save("Chess");
        for (int i = 0; i < 150; i++) {
            leaderboardRepository.save(game.getId(), "player" + i, i);
        }

        PageResponse<LeaderboardEntry> firstPage = controller.getAll(0, DEFAULT_PAGE_SIZE);
        PageResponse<LeaderboardEntry> secondPage = controller.getAll(1, DEFAULT_PAGE_SIZE);

        assertThat(firstPage.getContent()).hasSize(100);
        assertThat(secondPage.getContent()).hasSize(50);
        assertThat(firstPage.getTotalElements()).isEqualTo(150);
    }

    @Test
    void getByGame_customSize_paginatesToThatSize() {
        Game game = gameRepository.save("Chess");
        for (int i = 0; i < 10; i++) {
            leaderboardRepository.save(game.getId(), "player" + i, i);
        }

        PageResponse<LeaderboardEntry> topThree = controller.getByGame(game.getId(), 0, 3);
        PageResponse<LeaderboardEntry> secondPageOfThree = controller.getByGame(game.getId(), 1, 3);

        assertThat(topThree.getContent()).extracting(LeaderboardEntry::getScore)
                .containsExactly(9L, 8L, 7L);
        assertThat(topThree.getSize()).isEqualTo(3);
        assertThat(topThree.getTotalElements()).isEqualTo(10);
        assertThat(secondPageOfThree.getContent()).extracting(LeaderboardEntry::getScore)
                .containsExactly(6L, 5L, 4L);
    }

    @Test
    void getRankContext_fewerThan10AboveAndBelow_returnsWhatExists() {
        Game game = gameRepository.save("Chess");
        LeaderboardEntry first = leaderboardRepository.save(game.getId(), "alice", 90);
        LeaderboardEntry second = leaderboardRepository.save(game.getId(), "bob", 70);
        LeaderboardEntry third = leaderboardRepository.save(game.getId(), "carol", 50);

        LeaderboardContext context = controller.getRankContext(second.getId());

        assertThat(context.getRank()).isEqualTo(2);
        assertThat(context.getTotalElements()).isEqualTo(3);
        assertThat(context.getEntries()).extracting(LeaderboardEntry::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void getRankContext_manyEntries_returnsAtMost10AboveAndBelow() {
        Game game = gameRepository.save("Chess");
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            // Highest score first: player0 has the top score, so ranks are assigned 0..29.
            entries.add(leaderboardRepository.save(game.getId(), "player" + i, 100 - i));
        }
        LeaderboardEntry middle = entries.get(15);

        LeaderboardContext context = controller.getRankContext(middle.getId());

        assertThat(context.getRank()).isEqualTo(16);
        assertThat(context.getTotalElements()).isEqualTo(30);
        assertThat(context.getEntries()).hasSize(21);
        assertThat(context.getEntries().get(0).getId()).isEqualTo(entries.get(5).getId());
        assertThat(context.getEntries().get(10).getId()).isEqualTo(middle.getId());
        assertThat(context.getEntries().get(20).getId()).isEqualTo(entries.get(25).getId());
    }

    @Test
    void getRankContext_topRanked_hasNothingAboveIt() {
        Game game = gameRepository.save("Chess");
        LeaderboardEntry top = leaderboardRepository.save(game.getId(), "alice", 100);
        for (int i = 0; i < 15; i++) {
            leaderboardRepository.save(game.getId(), "player" + i, 90 - i);
        }

        LeaderboardContext context = controller.getRankContext(top.getId());

        assertThat(context.getRank()).isEqualTo(1);
        assertThat(context.getEntries()).hasSize(11);
        assertThat(context.getEntries().get(0).getId()).isEqualTo(top.getId());
    }

    @Test
    void getRankContext_nonExistentEntry_throwsLeaderboardEntryNotFoundException() {
        assertThrows(LeaderboardEntryNotFoundException.class, () -> controller.getRankContext(999L));
    }
}
