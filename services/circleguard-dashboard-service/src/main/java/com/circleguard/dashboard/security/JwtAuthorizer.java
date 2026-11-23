package com.circleguard.dashboard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.util.List;

/** Autoriza las consultas analiticas con el permiso dashboard:view. */
@Component
public class JwtAuthorizer {

    private final Key key;

    public JwtAuthorizer(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public void requirePermission(HttpServletRequest request, String permission) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(header.substring(7))
                    .getBody();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalido o expirado");
        }
        List<?> permissions = claims.get("permissions", List.class);
        if (permissions == null || !permissions.contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permiso requerido: " + permission);
        }
    }
}
