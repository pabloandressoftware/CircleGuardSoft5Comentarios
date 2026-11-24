package com.circleguard.form.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthorizerTest {

    private static final String SECRET = "test-jwt-secret-solo-para-pruebas-0123456789";

    private JwtAuthorizer authorizer;
    private Key key;
    private String subject;

    @BeforeEach
    void setUp() {
        authorizer = new JwtAuthorizer(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes());
        subject = UUID.randomUUID().toString();
    }

    private String token(List<String> permissions) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("permissions", permissions)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private MockHttpServletRequest request(String bearer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (bearer != null) {
            request.addHeader("Authorization", "Bearer " + bearer);
        }
        return request;
    }

    @Test
    void requireAuthenticatedWithoutHeaderReturns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authorizer.requireAuthenticated(request(null)));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void requireAuthenticatedWithInvalidTokenReturns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authorizer.requireAuthenticated(request("no-es-un-jwt")));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void requireAuthenticatedWithValidTokenPasses() {
        assertDoesNotThrow(() -> authorizer.requireAuthenticated(request(token(List.of("dashboard:view")))));
    }

    @Test
    void requirePermissionWithoutTokenReturns401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authorizer.requirePermission(request(null), "certificate:validate"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void requirePermissionWithoutPermissionReturns403() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authorizer.requirePermission(request(token(List.of("other"))), "certificate:validate"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void requirePermissionWithPermissionPasses() {
        assertDoesNotThrow(() -> authorizer.requirePermission(
                request(token(List.of("certificate:validate"))), "certificate:validate"));
    }

    @Test
    void subjectReturnsTokenSubject() {
        assertEquals(subject, authorizer.subject(request(token(List.of("x")))));
    }

    @Test
    void subjectReturnsNullForInvalidToken() {
        assertNull(authorizer.subject(request("invalido")));
        assertNull(authorizer.subject(request(null)));
    }
}
