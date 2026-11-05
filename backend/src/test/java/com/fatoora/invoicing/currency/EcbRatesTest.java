package com.fatoora.invoicing.currency;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Against a tiny local stand-in for the Frankfurter API that can be switched down and up. */
class EcbRatesTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 4);

    /** A clock the test moves forward by hand. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-04T09:00:00Z");

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

    final AtomicBoolean up = new AtomicBoolean(false);
    final AtomicInteger calls = new AtomicInteger();
    final TestClock clock = new TestClock();
    HttpServer server;
    EcbRates ecb;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = up.get()
                    ? """
                      {"base":"USD","date":"2026-11-04","rates":{"EUR":0.92,"GBP":0.78}}""".getBytes(StandardCharsets.UTF_8)
                    : "down".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(up.get() ? 200 : 503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ecb = new EcbRates("http://127.0.0.1:" + server.getAddress().getPort(), JsonMapper.builder().build(), clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aFailureIsNotCachedForeverOnlyBriefly() {
        assertThat(ecb.perUsd("EUR", DAY)).isEmpty();
        assertThat(calls).hasValue(1);

        // Straight after a failure, don't hammer the provider (or make every request wait).
        up.set(true);
        assertThat(ecb.perUsd("EUR", DAY)).isEmpty();
        assertThat(calls).hasValue(1);

        // After the retry window it tries again and recovers, without a restart.
        clock.now = clock.now.plus(EcbRates.RETRY_AFTER).plusSeconds(1);
        assertThat(ecb.perUsd("EUR", DAY)).contains(new BigDecimal("0.92"));
        assertThat(ecb.perUsd("USD", DAY)).contains(BigDecimal.ONE);
        assertThat(calls).hasValue(2);
    }

    @Test
    void successfulDaysAreCached() {
        up.set(true);
        assertThat(ecb.perUsd("GBP", DAY)).contains(new BigDecimal("0.78"));
        assertThat(ecb.perUsd("EUR", DAY)).contains(new BigDecimal("0.92"));
        assertThat(calls).hasValue(1);

        up.set(false); // later outages don't affect a day already fetched
        assertThat(ecb.perUsd("EUR", DAY)).contains(new BigDecimal("0.92"));
    }

    @Test
    void anUnknownCurrencyIsEmptyNotAnError() {
        up.set(true);
        assertThat(ecb.perUsd("XYZ", DAY)).isEmpty();
    }
}
