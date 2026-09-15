package com.example.leaderboard.integration;

import com.example.leaderboard.integration.support.ThrowingLeaderboardRepository;
import com.example.leaderboard.repository.LeaderboardRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replaces the leaderboard repository bean with one that throws an exception type not
 * otherwise recognized by GlobalExceptionHandler, to confirm the catch-all fallback
 * kicks in over the real HTTP stack: a clean 500 that doesn't leak internal details.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnknownErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class FailingRepositoryConfig {
        @Bean
        @Primary
        LeaderboardRepository leaderboardRepository() {
            return new ThrowingLeaderboardRepository(
                    () -> new IllegalStateException("simulated: unexpected internal failure"));
        }
    }

    @Test
    void unrecognizedException_returns500WithoutLeakingInternalDetails() throws Exception {
        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message", Matchers.not(Matchers.containsString("simulated"))));
    }
}
