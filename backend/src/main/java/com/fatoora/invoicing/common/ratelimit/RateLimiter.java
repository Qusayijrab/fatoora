package com.fatoora.invoicing.common.ratelimit;

import com.fatoora.invoicing.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters kept in memory: at most {@code limit} hits per key per window.
 * Enough for the single-server deployment in infra/docker-compose.prod.yml; with several
 * backend instances each one counts on its own (move the counters to Redis or Postgres
 * then). Expired windows are swept as the map grows, so memory stays bounded.
 */
@Component
public class RateLimiter {

    /** Too many requests: 429, with Retry-After in seconds. */
    public static class LimitExceeded extends ApiException {
        private final long retryAfterSeconds;

        LimitExceeded(String detail, long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, detail);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private record Window(Instant start, int count) {}

    private static final int SWEEP_ABOVE = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one hit for {@code bucket}/{@code key}; throws {@link LimitExceeded} once over {@code limit}. */
    public void hit(String bucket, String key, int limit, Duration window, String message) {
        Instant now = clock.instant();
        Window w = windows.compute(bucket + ':' + key, (k, old) ->
                old == null || !now.isBefore(old.start().plus(window)) ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (windows.size() > SWEEP_ABOVE) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(window)));
        }
        if (w.count() > limit) {
            long retry = Math.max(1, Duration.between(now, w.start().plus(window)).toSeconds());
            throw new LimitExceeded(message, retry);
        }
    }
}
