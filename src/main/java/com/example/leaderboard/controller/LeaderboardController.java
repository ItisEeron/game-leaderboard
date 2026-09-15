package com.example.leaderboard.controller;

import com.example.leaderboard.exception.GameNotFoundException;
import com.example.leaderboard.model.LeaderboardEntry;
import com.example.leaderboard.model.PageResponse;
import com.example.leaderboard.repository.GameRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    static final int PAGE_SIZE = 100;

    private final LeaderboardRepository leaderboardRepository;
    private final GameRepository gameRepository;

    public LeaderboardController(LeaderboardRepository leaderboardRepository, GameRepository gameRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.gameRepository = gameRepository;
    }

    @GetMapping
    public PageResponse<LeaderboardEntry> getAll(@RequestParam(defaultValue = "0") int page) {
        return paginate(new ArrayList<>(leaderboardRepository.findAll()), page);
    }

    @GetMapping("/{id}")
    public LeaderboardEntry getById(@PathVariable Long id) {
        return leaderboardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leaderboard entry not found: " + id));
    }

    /**
     * Ranked entries for a single game: highest score first, ties broken by earliest submission.
     */
    @GetMapping("/game/{gameId}")
    public PageResponse<LeaderboardEntry> getByGame(@PathVariable Long gameId, @RequestParam(defaultValue = "0") int page) {
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }
        return paginate(leaderboardRepository.findByGameIdRanked(gameId), page);
    }

    @PostMapping
    public ResponseEntity<LeaderboardEntry> create(@RequestBody LeaderboardEntry entry) {
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

    private <T> PageResponse<T> paginate(List<T> items, int page) {
        int from = Math.min(page * PAGE_SIZE, items.size());
        int to = Math.min(from + PAGE_SIZE, items.size());
        return new PageResponse<>(items.subList(from, to), page, PAGE_SIZE, items.size());
    }
}
