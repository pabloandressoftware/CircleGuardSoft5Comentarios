package com.circleguard.auth.controller;

import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.VisitorHandoffService;
import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.client.IdentityUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;
    private final VisitorHandoffService visitorHandoffService;

    /** HU-23: tiempo sugerido al cliente cuando el circuito de identity esta abierto. */
    @org.springframework.beans.factory.annotation.Value(
            "${resilience4j.circuitbreaker.instances.identity.wait-duration-in-open-state:10s}")
    private java.time.Duration identityRetryAfter = java.time.Duration.ofSeconds(10);

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            // 1. Authenticate (Dual-Chain)
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // 2. Anonymize (Fetch/Create Anonymous ID from Identity Service)
            UUID anonymousId = identityClient.getAnonymousId(username);

            // 3. Issue Token
            String token = jwtService.generateToken(anonymousId, auth);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "anonymousId", anonymousId.toString()
            ));
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.warn("Login rechazado");
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (IdentityUnavailableException e) {
            // HU-23: identity caido o con el circuito abierto -> 503 controlado y rapido.
            log.warn("Login degradado: identity-service no disponible");
            return ResponseEntity.status(503)
                    .header("Retry-After", String.valueOf(identityRetryAfter.toSeconds()))
                    .body(Map.of(
                            "message", "El servicio de identidad no esta disponible. Intenta de nuevo en unos segundos.",
                            "code", "IDENTITY_UNAVAILABLE"));
        } catch (Exception e) {
            log.error("Error inesperado durante el login", e);
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error"));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<?> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        String handoffToken = request.get("handoffToken");
        if (anonymousIdStr == null || handoffToken == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "anonymousId y handoffToken son obligatorios"));
        }

        UUID anonymousId;
        try {
            anonymousId = UUID.fromString(anonymousIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "anonymousId invalido"));
        }

        // HU-21: solo se emite el JWT si identity firmo este anonymousId.
        if (!visitorHandoffService.verify(anonymousId, handoffToken)) {
            return ResponseEntity.status(401).body(Map.of("message", "Handoff token invalido o expirado"));
        }

        Authentication visitorAuth = new UsernamePasswordAuthenticationToken(
                anonymousIdStr,
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("VISITOR"))
        );

        String token = jwtService.generateToken(anonymousId, visitorAuth);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "handoffPayload", "HANDOFF_TOKEN:" + anonymousId.toString() + ":" + token
        ));
    }
}
