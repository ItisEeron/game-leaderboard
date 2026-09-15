package com.example.leaderboard.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the repositories through the real controllers under concurrent load, to
 * confirm the underlying stores don't lose writes or throw under contention, and that
 * a delete racing a read for the same entry never produces a server error.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentSubmissions_allSucceedWithUniqueIdsAndCorrectRanking() throws Exception {
        long gameId = createGame("Concurrent Chess " + System.nanoTime());
        int threadCount = 50;

        List<Integer> ids = runConcurrently(threadCount, i -> {
            String body = objectMapper.writeValueAsString(
                    Map.of("gameId", gameId, "playerName", "player" + i, "score", i));
            MvcResult result = mockMvc.perform(post("/api/leaderboard")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
        });

        assertThat(ids).hasSize(threadCount);
        assertThat(Set.copyOf(ids)).as("ids must be unique - no lost or duplicated writes").hasSize(threadCount);

        MvcResult ranked = mockMvc.perform(get("/api/leaderboard/game/" + gameId)).andReturn();
        JsonNode content = objectMapper.readTree(ranked.getResponse().getContentAsString()).get("content");
        assertThat(content).hasSize(threadCount);

        int previousScore = Integer.MAX_VALUE;
        for (JsonNode node : content) {
            int score = node.get("score").asInt();
            assertThat(score).isLessThanOrEqualTo(previousScore);
            previousScore = score;
        }
    }

    @Test
    void concurrentGetAndDelete_neverErrorsAndEndsConsistent() throws Exception {
        long gameId = createGame("Race Chess " + System.nanoTime());
        long entryId = createEntry(gameId, "racer", 42);

        int readerCount = 20;
        int deleterCount = 5;
        int totalThreads = readerCount + deleterCount;

        List<Integer> statuses = runConcurrently(totalThreads, i -> {
            if (i < readerCount) {
                return mockMvc.perform(get("/api/leaderboard/" + entryId)).andReturn().getResponse().getStatus();
            }
            return mockMvc.perform(delete("/api/leaderboard/" + entryId)).andReturn().getResponse().getStatus();
        });

        // No request should ever blow up with a server error, regardless of who "wins" the race.
        assertThat(statuses).as("no request should ever 5xx under a get/delete race").allMatch(s -> s < 500);

        List<Integer> getStatuses = statuses.subList(0, readerCount);
        assertThat(getStatuses).allMatch(s -> s == 200 || s == 404);

        List<Integer> deleteStatuses = statuses.subList(readerCount, totalThreads);
        assertThat(deleteStatuses).allMatch(s -> s == 204 || s == 404);
        assertThat(deleteStatuses).filteredOn(s -> s == 204)
                .as("exactly one concurrent delete should actually remove the entry")
                .hasSize(1);

        mockMvc.perform(get("/api/leaderboard/" + entryId)).andExpect(status().isNotFound());
    }

    /**
     * Runs one task per thread, all released at once via a shared latch so they contend
     * for the same resource simultaneously rather than running near-sequentially.
     */
    private <T> List<T> runConcurrently(int threadCount, ThrowingIntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<T>> tasks = IntStream.range(0, threadCount)
                    .<Callable<T>>mapToObj(i -> () -> {
                        ready.countDown();
                        start.await();
                        return task.apply(i);
                    })
                    .collect(Collectors.toList());

            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> t : tasks) {
                futures.add(pool.submit(t));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    @FunctionalInterface
    private interface ThrowingIntFunction<T> {
        T apply(int i) throws Exception;
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
