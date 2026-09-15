package com.example.leaderboard.idempotency;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * A snapshot of a successful response, replayed verbatim for a repeated request that
 * carries the same idempotency key.
 */
record CachedResponse(int status, byte[] body, String contentType) {

    boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    void writeTo(HttpServletResponse response) throws IOException {
        response.setStatus(status);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        response.getOutputStream().write(body);
    }

    static CachedResponse from(ContentCachingResponseWrapper wrapper) {
        return new CachedResponse(wrapper.getStatus(), wrapper.getContentAsByteArray(), wrapper.getContentType());
    }
}
