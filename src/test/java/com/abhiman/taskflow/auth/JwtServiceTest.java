package com.abhiman.taskflow.auth;

import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1kZWZpbml0ZWx5LTI1Ni1iaXRzLWxvbmc=";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1));

    private static User user(long id) {
        User user = new User("Ada", "ada@example.com", "hash", Role.MANAGER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenVerifiesToSameUser() {
        JwtService jwt = serviceAt(NOW);

        String token = jwt.issueToken(user(42));

        assertThat(jwt.verify(token)).contains(new AuthenticatedUser(42L, "ada@example.com", Role.MANAGER));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(NOW).issueToken(user(42));

        JwtService oneHourLater = serviceAt(NOW.plus(Duration.ofHours(1)).plusSeconds(1));

        assertThat(oneHourLater.verify(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = serviceAt(NOW);
        String token = jwt.issueToken(user(42));

        // Flip one character in the payload; the signature no longer matches.
        String[] parts = token.split("\\.");
        char c = parts[1].charAt(5);
        parts[1] = parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A') + parts[1].substring(6);

        assertThat(jwt.verify(String.join(".", parts))).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String otherSecret = "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1kZWZpbml0ZWx5LTI1Ni1iaXRzLWxvbmc=";
        JwtService attacker = new JwtService(new JwtProperties(otherSecret, Duration.ofHours(1)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(serviceAt(NOW).verify(attacker.issueToken(user(1)))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW).verify("not-a-jwt")).isEmpty();
    }
}
