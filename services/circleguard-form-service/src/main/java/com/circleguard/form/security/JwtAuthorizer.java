package com.circleguard.form.security;

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

/**
 * Autorizacion basada en el JWT que emite auth-service. Se aplica solo a los
 * endpoints administrativos; el envio de encuestas sigue siendo publico.
 */
@Component
public class JwtAuthorizer {

    private final Key key;

    public JwtAuthorizer(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public void requireAuthenticated(HttpServletRequest request) {
        if (claims(request) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }
    }

    public void requirePermission(HttpServletRequest request, String permission) {
        Claims claims = claims(request);
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token requerido");
        }
        List<?> permissions = claims.get("permissions", List.class);
        if (permissions == null || !permissions.contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permiso requerido: " + permission);
        }
    }

    public String subject(HttpServletRequest request) {
        Claims claims = claims(request);
        return claims == null ? null : claims.getSubject();
    }

    private Claims claims(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(header.substring(7))
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
