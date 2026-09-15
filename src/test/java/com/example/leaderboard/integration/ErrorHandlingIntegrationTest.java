package com.example.leaderboard.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real HTTP stack (validation, controllers, GlobalExceptionHandler) end to
 * end for every client-facing error case.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createGame_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("name must not be blank"));
    }

    @Test
    void createEntry_missingGameId_returns400() throws Exception {
        mockMvc.perform(post("/api/leaderboard").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerName\":\"alice\",\"score\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("gameId must not be null"));
    }

    @Test
    void createEntry_blankPlayerName_returns400() throws Exception {
        mockMvc.perform(post("/api/leaderboard").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":1,\"playerName\":\"\",\"score\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("playerName must not be blank"));
    }

    @Test
    void createEntry_nonExistentGame_returns404() throws Exception {
        mockMvc.perform(post("/api/leaderboard").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":999999,\"playerName\":\"alice\",\"score\":50}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Game not found: 999999"));
    }

    @Test
    void malformedJsonBody_returns400() throws Exception {
        mockMvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON)
                        .content("{name: chess"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void nonNumericPathVariable_returns400() throws Exception {
        mockMvc.perform(get("/api/games/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void getNonExistentGame_returns404() throws Exception {
        mockMvc.perform(get("/api/games/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Game not found: 999999"));
    }

    @Test
    void getNonExistentLeaderboardEntry_returns404() throws Exception {
        mockMvc.perform(get("/api/leaderboard/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Leaderboard entry not found: 999999"));
    }

    @Test
    void deleteNonExistentLeaderboardEntry_returns404() throws Exception {
        mockMvc.perform(delete("/api/leaderboard/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNonExistentGame_returns404() throws Exception {
        mockMvc.perform(delete("/api/games/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Game not found: 999999"));
    }
}
