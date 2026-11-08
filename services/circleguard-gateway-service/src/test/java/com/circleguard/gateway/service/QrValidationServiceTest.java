package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QrValidationServiceTest {

    private QrValidationService service;
    private ValueOperations<String, String> valueOps;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new QrValidationService(redisTemplate);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    private String tokenFor(String anonymousId, long validForMillis) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(anonymousId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + validForMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @ParameterizedTest(name = "{0} entra al campus")
    @ValueSource(strings = {"ACTIVE", "RECOVERED"})
    void allowsHealthyStatuses(String status) {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn(status);

        QrValidationService.ValidationResult result = service.validateToken(tokenFor(anonymousId, 60_000));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void allowsUserWithoutStatusInRedis() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn(null);

        QrValidationService.ValidationResult result = service.validateToken(tokenFor(anonymousId, 60_000));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @ParameterizedTest(name = "{0} no entra al campus")
    @ValueSource(strings = {"SUSPECT", "PROBABLE", "CONFIRMED"})
    void deniesStatusesInsideAFence(String status) {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn(status);

        QrValidationService.ValidationResult result = service.validateToken(tokenFor(anonymousId, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void deniesUnknownStatus() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(tokenFor(anonymousId, 60_000));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Unknown Health Status", result.message());
    }

    @Test
    void deniesExpiredToken() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("ACTIVE");

        QrValidationService.ValidationResult result = service.validateToken(tokenFor(anonymousId, -1_000));

        assertFalse(result.valid());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void deniesTokenSignedWithAnotherSecret() {
        Key otherKey = Keys.hmacShaKeyFor("another-secret-key-with-32-chars-min!!".getBytes());
        String forged = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(forged);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }
}
