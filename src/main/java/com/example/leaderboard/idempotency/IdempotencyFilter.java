package com.example.leaderboard.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lets a client mark a mutating request (POST) with an {@code Idempotency-Key} header
 * so that retrying it - e.g. after a dropped connection where the client can't tell if
 * the original write succeeded - replays the original response instead of creating a
 * second copy of the resource.
 *
 * <p>Only successful (2xx) responses are cached: a request that failed validation, hit
 * a 404, etc. didn't create anything, so the same key can be retried freely once the
 * problem is fixed rather than being permanently stuck replaying an old error.
 *
 * <p>Concurrent requests carrying the same key are serialized: the second waits for the
 * first to finish and replays its result, rather than racing it to create a duplicate.
 *
 * <p>The store is in-memory and per-instance, so it does not dedupe across multiple app
 * instances behind a load balancer - see the architecture doc's Known Tradeoffs.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "Idempotency-Key";
    static final String REPLAY_HEADER_NAME = "Idempotent-Replay";

    private static final long WAIT_TIMEOUT_SECONDS = 30;

    private final ConcurrentHashMap<String, CompletableFuture<CachedResponse>> results = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER_NAME);
        if (!"POST".equalsIgnoreCase(request.getMethod()) || idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String cacheKey = request.getRequestURI() + "#" + idempotencyKey;
        CompletableFuture<CachedResponse> ownFuture = new CompletableFuture<>();
        CompletableFuture<CachedResponse> inFlight = results.putIfAbsent(cacheKey, ownFuture);

        if (inFlight == null) {
            executeAndCache(request, response, chain, cacheKey, ownFuture);
        } else {
            replayOrFallThrough(request, response, chain, inFlight);
        }
    }

    private void executeAndCache(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                  String cacheKey, CompletableFuture<CachedResponse> ownFuture)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            CachedResponse cached = CachedResponse.from(wrapper);
            if (cached.isSuccess()) {
                ownFuture.complete(cached);
            } else {
                results.remove(cacheKey);
                ownFuture.completeExceptionally(new IllegalStateException("Request did not succeed; not cached"));
            }
        } catch (Exception ex) {
            results.remove(cacheKey);
            ownFuture.completeExceptionally(ex);
            throw ex;
        } finally {
            wrapper.copyBodyToResponse();
        }
    }

    private void replayOrFallThrough(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                      CompletableFuture<CachedResponse> inFlight)
            throws ServletException, IOException {
        try {
            CachedResponse cached = inFlight.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            response.setHeader(REPLAY_HEADER_NAME, "true");
            cached.writeTo(response);
        } catch (ExecutionException | TimeoutException e) {
            // The original attempt failed or timed out - it made no lasting change, so let
            // this request run for real instead of forcing the caller to mint a new key.
            chain.doFilter(request, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException("Interrupted while waiting for the in-flight idempotent request", e);
        }
    }
}
