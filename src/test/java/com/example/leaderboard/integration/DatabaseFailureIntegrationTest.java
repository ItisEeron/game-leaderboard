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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replaces the leaderboard repository bean with one that always fails the way a real
 * database outage would, to confirm the full stack (not just GlobalExceptionHandler in
 * isolation - see GlobalExceptionHandlerTest) surfaces it as 503 without leaking
 * internal details to the client.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class FailingRepositoryConfig {
        @Bean
        @Primary
        LeaderboardRepository leaderboardRepository() {
            return new ThrowingLeaderboardRepository(
                    () -> new DataAccessResourceFailureException("simulated: connection refused to db-host:5432"));
        }
    }

    @Test
    void databaseOutage_returns503WithoutLeakingInternalDetails() throws Exception {
        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Service temporarily unavailable, please try again later"))
                .andExpect(jsonPath("$.message", Matchers.not(Matchers.containsString("db-host"))));
    }
}
