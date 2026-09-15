package com.example.leaderboard.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage, over the real HTTP stack, for configurable leaderboard page
 * sizes and a player's rank context (self plus up to 10 entries above/below).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeaderboardFeaturesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getByGame_customSize_limitsResultsToThatSize() throws Exception {
        long gameId = createGame("Size Test " + System.nanoTime());
        for (int i = 0; i < 10; i++) {
            createEntry(gameId, "player" + i, i);
        }

        mockMvc.perform(get("/api/leaderboard/game/" + gameId).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.content[0].score").value(9))
                .andExpect(jsonPath("$.content[1].score").value(8));

        mockMvc.perform(get("/api/leaderboard/game/" + gameId).param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(10)))
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void getRankContext_withFewerThan10AboveAndBelow_returnsWhatExists() throws Exception {
        long gameId = createGame("Rank Test Small " + System.nanoTime());
        long first = createEntry(gameId, "alice", 90);
        long second = createEntry(gameId, "bob", 70);
        long third = createEntry(gameId, "carol", 50);

        MvcResult result = mockMvc.perform(get("/api/leaderboard/" + second + "/rank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(3)))
                .andReturn();

        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString()).get("entries");
        assertThat(entries.get(0).get("id").asLong()).isEqualTo(first);
        assertThat(entries.get(1).get("id").asLong()).isEqualTo(second);
        assertThat(entries.get(2).get("id").asLong()).isEqualTo(third);
    }

    @Test
    void getRankContext_withManyEntries_capsAt10AboveAndBelow() throws Exception {
        long gameId = createGame("Rank Test Large " + System.nanoTime());
        long[] ids = new long[30];
        for (int i = 0; i < 30; i++) {
            // Highest score first: player0 has the top score, ranks are assigned 1..30 in order.
            ids[i] = createEntry(gameId, "player" + i, 100 - i);
        }
        long middleEntryId = ids[15];

        MvcResult result = mockMvc.perform(get("/api/leaderboard/" + middleEntryId + "/rank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(16))
                .andExpect(jsonPath("$.totalElements").value(30))
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(21)))
                .andReturn();

        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString()).get("entries");
        assertThat(entries.get(0).get("id").asLong()).isEqualTo(ids[5]);
        assertThat(entries.get(10).get("id").asLong()).isEqualTo(ids[15]);
        assertThat(entries.get(20).get("id").asLong()).isEqualTo(ids[25]);
    }

    private long createGame(String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));
        MvcResult result = mockMvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createEntry(long gameId, String playerName, int score) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("gameId", gameId, "playerName", playerName, "score", score));
        MvcResult result = mockMvc.perform(post("/api/leaderboard")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
