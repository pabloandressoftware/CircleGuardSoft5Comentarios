package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrTokenServiceTest {

    private static final String SECRET = "my-qr-secret-key-for-tests-1234567890";

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Test
    void expirationIsMeasuredInMilliseconds() {
        QrTokenService service = new QrTokenService(SECRET, 60_000);
        UUID anonymousId = UUID.randomUUID();

        Claims claims = parse(service.generateQrToken(anonymousId));

        long validForMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        // JWT guarda segundos enteros: se admite 1 s de redondeo
        assertTrue(Math.abs(validForMillis - 60_000) <= 1_000, "vigencia esperada ~60 s, fue " + validForMillis + " ms");
        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    void reportsExpirationInSecondsForTheClient() {
        assertEquals(60, new QrTokenService(SECRET, 60_000).getExpirationSeconds());
        assertEquals(300, new QrTokenService(SECRET, 300_000).getExpirationSeconds());
    }

    @Test
    void rejectsValuesThatLookLikeSeconds() {
        // El valor anterior del repositorio (300) daba un QR de 0,3 s
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new QrTokenService(SECRET, 300));
        assertTrue(error.getMessage().contains("milisegundos"));
    }
}
