package com.example.leaderboard.controller;

import com.example.leaderboard.exception.GameNotFoundException;
import com.example.leaderboard.exception.LeaderboardEntryNotFoundException;
import com.example.leaderboard.model.LeaderboardContext;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.PageResponse;
import com.example.leaderboard.repository.GameRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    static final int DEFAULT_PAGE_SIZE = 100;
    static final int RANK_CONTEXT_SIZE = 10;

    private final LeaderboardRepository leaderboardRepository;
    private final GameRepository gameRepository;

    public LeaderboardController(LeaderboardRepository leaderboardRepository, GameRepository gameRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.gameRepository = gameRepository;
    }

    @GetMapping
    public PageResponse<LeaderboardEntry> getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(value = 1, message = "size must be at least 1") int size) {
        return paginate(new ArrayList<>(leaderboardRepository.findAll()), page, size);
    }

    @GetMapping("/{id}")
    public LeaderboardEntry getById(@PathVariable Long id) {
        return leaderboardRepository.findById(id)
                .orElseThrow(() -> new LeaderboardEntryNotFoundException(id));
    }

    /**
     * Ranked entries for a single game: highest score first, ties broken by earliest submission.
     */
    @GetMapping("/game/{gameId}")
    public PageResponse<LeaderboardEntry> getByGame(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(value = 1, message = "size must be at least 1") int size) {
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }
        return paginate(leaderboardRepository.findByGameIdRanked(gameId), page, size);
    }

    /**
     * A single entry's rank within its game, plus up to {@link #RANK_CONTEXT_SIZE} entries
     * immediately above and below it (fewer if it's near the top or bottom).
     */
    @GetMapping("/{id}/rank")
    public LeaderboardContext getRankContext(@PathVariable Long id) {
        LeaderboardEntry entry = leaderboardRepository.findById(id)
                .orElseThrow(() -> new LeaderboardEntryNotFoundException(id));

        List<LeaderboardEntry> ranked = leaderboardRepository.findByGameIdRanked(entry.getGameId());
        int index = indexOfById(ranked, id);
        if (index == -1) {
            throw new LeaderboardEntryNotFoundException(id);
        }

        int from = Math.max(0, index - RANK_CONTEXT_SIZE);
        int to = Math.min(ranked.size(), index + RANK_CONTEXT_SIZE + 1);
        return new LeaderboardContext(index + 1L, ranked.size(), ranked.subList(from, to));
    }

    private int indexOfById(List<LeaderboardEntry> entries, Long id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @PostMapping
    public ResponseEntity<LeaderboardEntry> create(@Valid @RequestBody LeaderboardEntry entry) {
        if (!gameRepository.existsById(entry.getGameId())) {
            throw new GameNotFoundException(entry.getGameId());
        }
        LeaderboardEntry created = leaderboardRepository.save(entry.getGameId(), entry.getPlayerName(), entry.getScore());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return leaderboardRepository.deleteById(id)
                .map(removed -> ResponseEntity.noContent().<Void>build())
                .orElse(ResponseEntity.notFound().build());
    }

    private <T> PageResponse<T> paginate(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageResponse<>(items.subList(from, to), page, size, items.size());
    }
}
