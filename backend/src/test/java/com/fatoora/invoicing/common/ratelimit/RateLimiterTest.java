package com.fatoora.invoicing.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-08T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final TestClock clock = new TestClock();
    final RateLimiter limiter = new RateLimiter(clock);

    void hit(String key) {
        limiter.hit("login", key, 3, Duration.ofMinutes(10), "slow down");
    }

    @Test
    void allowsTheLimitThenRefusesUntilTheWindowEnds() {
        hit("a");
        hit("a");
        hit("a");
        assertThatThrownBy(() -> hit("a"))
                .isInstanceOf(RateLimiter.LimitExceeded.class)
                .hasMessage("slow down")
                .satisfies(e -> assertThat(((RateLimiter.LimitExceeded) e).getRetryAfterSeconds()).isEqualTo(600));

        clock.now = clock.now.plus(Duration.ofMinutes(4));
        assertThatThrownBy(() -> hit("a"))
                .satisfies(e -> assertThat(((RateLimiter.LimitExceeded) e).getRetryAfterSeconds()).isEqualTo(360));

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertThatCode(() -> hit("a")).doesNotThrowAnyException();
    }

    @Test
    void keysAndBucketsAreCountedSeparately() {
        hit("a");
        hit("a");
        hit("a");
        assertThatCode(() -> hit("b")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.hit("register", "a", 3, Duration.ofMinutes(10), "x")).doesNotThrowAnyException();
    }

    @Test
    void theClientIpComesFromTheFirstForwardedAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5"); // the Next.js server
        assertThat(RateLimits.clientIp(request)).isEqualTo("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        assertThat(RateLimits.clientIp(request)).isEqualTo("203.0.113.7");
        assertThat(RateLimits.emailKey("  Lina@Example.COM ")).isEqualTo("lina@example.com");
    }
}
