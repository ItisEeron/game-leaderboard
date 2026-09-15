package com.example.leaderboard.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises IdempotencyFilter over the real HTTP stack: a repeated POST carrying the
 * same Idempotency-Key must replay the original result instead of creating a duplicate,
 * concurrent duplicates must not race each other into creating two copies, a failed
 * request must not "poison" the key, and different endpoints/games must not collide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void repeatedPost_sameIdempotencyKey_returnsOriginalWithoutCreatingDuplicate() throws Exception {
        long gameId = createGame("Idempotency Chess " + System.nanoTime());
        String key = "key-" + System.nanoTime();
        String body = objectMapper.writeValueAsString(
                Map.of("gameId", gameId, "playerName", "alice", "score", 90));

        MvcResult first = mockMvc.perform(post("/api/leaderboard")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/leaderboard")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(get("/api/leaderboard/game/" + gameId))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void repeatedPost_withoutIdempotencyKey_createsSeparateEntries() throws Exception {
        long gameId = createGame("No Key Chess " + System.nanoTime());
        String body = objectMapper.writeValueAsString(
                Map.of("gameId", gameId, "playerName", "bob", "score", 50));

        mockMvc.perform(post("/api/leaderboard").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/leaderboard").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/leaderboard/game/" + gameId))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void failedRequest_withIdempotencyKey_isNotCached_soRetryWithFixedBodySucceeds() throws Exception {
        String key = "retry-key-" + System.nanoTime();

        mockMvc.perform(post("/api/leaderboard")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameId\":999999,\"playerName\":\"carol\",\"score\":10}"))
                .andExpect(status().isNotFound());

        long gameId = createGame("Retry Chess " + System.nanoTime());
        mockMvc.perform(post("/api/leaderboard")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("gameId", gameId, "playerName", "carol", "score", 10))))
                .andExpect(status().isCreated());
    }

    @Test
    void sameIdempotencyKeyValue_onDifferentEndpoints_doesNotCollide() throws Exception {
        String key = "shared-key-" + System.nanoTime();
        long gameId = createGameWithKey("Cross Endpoint Chess " + System.nanoTime(), key);

        mockMvc.perform(post("/api/leaderboard")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("gameId", gameId, "playerName", "dave", "score", 20))))
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentDuplicatePosts_sameKey_onlyOneEntryIsCreated() throws Exception {
        long gameId = createGame("Concurrent Idempotency Chess " + System.nanoTime());
        String key = "concurrent-key-" + System.nanoTime();
        String body = objectMapper.writeValueAsString(
                Map.of("gameId", gameId, "playerName", "racer", "score", 77));

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Long>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Long>>mapToObj(i -> () -> {
                        ready.countDown();
                        start.await();
                        MvcResult result = mockMvc.perform(post("/api/leaderboard")
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andReturn();
                        assertThat(result.getResponse().getStatus()).isEqualTo(201);
                        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
                    })
                    .collect(Collectors.toList());

            List<Future<Long>> futures = new java.util.ArrayList<>();
            for (Callable<Long> task : tasks) {
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();

            List<Long> ids = new java.util.ArrayList<>();
            for (Future<Long> future : futures) {
                ids.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(Set.copyOf(ids)).as("every concurrent duplicate must return the same id").hasSize(1);
        } finally {
            pool.shutdown();
        }

        mockMvc.perform(get("/api/leaderboard/game/" + gameId))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private long createGame(String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));
        MvcResult result = mockMvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createGameWithKey(String name, String idempotencyKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));
        MvcResult result = mockMvc.perform(post("/api/games")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
