package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "jwt-secret-for-tests-1234567890-abcdefghij";

    @Test
    void tokenCarriesSubjectAndPermissions() {
        JwtTokenService service = new JwtTokenService(SECRET, 3_600_000);
        UUID id = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("u", "p",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("gate:validate")));

        String token = service.generateToken(id, auth);

        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build().parseClaimsJws(token).getBody();
        assertEquals(id.toString(), claims.getSubject());
        assertTrue(((List<?>) claims.get("permissions")).containsAll(List.of("ROLE_ADMIN", "gate:validate")));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }
}
