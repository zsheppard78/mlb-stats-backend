package com.baseballstats.baseballstats.account;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain checks that don't need the database. */
class AccountRulesTest {

    @Test
    void usernames() {
        assertEquals("zach_s", AccountRules.normalizeUsername("  Zach_S "));
        AccountRules.checkUsername("zach_s");
        assertThrows(ApiException.class, () -> AccountRules.checkUsername("zs"));
        assertThrows(ApiException.class, () -> AccountRules.checkUsername("zach.s"));
    }

    @Test
    void passwords() {
        AccountRules.checkPassword("eight888");
        assertThrows(ApiException.class, () -> AccountRules.checkPassword("seven77"));
        assertThrows(ApiException.class, () -> AccountRules.checkPassword("x".repeat(73)));
    }

    @Test
    void tokens() {
        String token = Tokens.newToken();
        assertEquals(43, token.length());
        assertEquals(64, Tokens.hash(token).length());
        assertEquals(token, Tokens.fromHeader("Bearer " + token));
        assertNull(Tokens.fromHeader("Basic abc"));
        assertNull(Tokens.fromHeader(null));
    }

    @Test
    void limiterBlocksAfterTooManyTriesThenForgets() {
        Instant[] now = {Instant.parse("2026-10-20T12:00:00Z")};
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now[0]; }
        };
        AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 3; i++) limiter.record("zach");
        assertTrue(limiter.isBlocked("zach"));
        assertFalse(limiter.isBlocked("someone_else"));
        now[0] = now[0].plus(Duration.ofMinutes(16));
        assertFalse(limiter.isBlocked("zach"));
    }

    @Test
    void googleClaims() {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        Map<String, String> good = Map.of("aud", "client-1", "iss", "https://accounts.google.com", "sub", "1234",
                "exp", String.valueOf(now.getEpochSecond() + 600), "email", "fan@example.com",
                "email_verified", "true", "name", "Zach");
        GoogleTokenVerifier.GoogleUser user = GoogleTokenVerifier.checkClaims(good, "client-1", now);
        assertEquals("1234", user.subject());
        assertEquals("fan@example.com", user.email());

        assertThrows(ApiException.class, () -> GoogleTokenVerifier.checkClaims(good, "other-client", now));
        assertThrows(ApiException.class, () -> GoogleTokenVerifier.checkClaims(good, "client-1", now.plusSeconds(601)));
        Map<String, String> wrongIssuer = new java.util.HashMap<>(good);
        wrongIssuer.put("iss", "evil.example.com");
        assertThrows(ApiException.class, () -> GoogleTokenVerifier.checkClaims(wrongIssuer, "client-1", now));
        Map<String, String> unverified = new java.util.HashMap<>(good);
        unverified.put("email_verified", "false");
        assertNull(GoogleTokenVerifier.checkClaims(unverified, "client-1", now).email());
    }
}
