package com.example.leaderboard.controller;

import com.example.leaderboard.exception.GameNotFoundException;
import com.example.leaderboard.model.Game;
import com.example.leaderboard.repository.GameRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameRepository gameRepository;

    public GameController(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @GetMapping
    public Collection<Game> getAll() {
        return gameRepository.findAll();
    }

    @GetMapping("/{id}")
    public Game getById(@PathVariable Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new GameNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<Game> create(@Valid @RequestBody Game game) {
        Game created = gameRepository.save(game.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return gameRepository.deleteById(id)
                .map(removed -> ResponseEntity.noContent().<Void>build())
                .orElseThrow(() -> new GameNotFoundException(id));
    }
}
