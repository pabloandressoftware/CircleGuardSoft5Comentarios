package com.circleguard.identity.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "jwt-secret-for-tests-1234567890-abcdefghij";
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void run(String header) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (header != null) req.addHeader("Authorization", header);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    }

    private String token(long ttlMillis) {
        return Jwts.builder().setSubject("anon-1").claim("permissions", List.of("identity:lookup"))
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes())).compact();
    }

    @Test
    void validTokenAuthenticatesWithItsPermissions() throws Exception {
        run("Bearer " + token(60_000));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("anon-1", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("identity:lookup")));
    }

    @Test
    void missingOrExpiredOrGarbageTokenLeavesRequestAnonymous() throws Exception {
        run(null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        run("Bearer " + token(-60_000));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        run("Bearer basura");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
