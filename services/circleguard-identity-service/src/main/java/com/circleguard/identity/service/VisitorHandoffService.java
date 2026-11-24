package com.circleguard.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Emite un comprobante firmado (HMAC) para el handoff de visitantes.
 *
 * El auth-service no debe firmar un JWT solo porque el cliente envie un
 * anonymousId: cualquiera podria suplantar a otro visitante. Identity firma el
 * anonymousId con una clave compartida (JWT_SECRET) y auth verifica la firma
 * antes de emitir el JWT. El comprobante caduca en pocos minutos.
 */
@Service
public class VisitorHandoffService {

    private static final long TTL_SECONDS = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public VisitorHandoffService(@Value("${jwt.secret}") String jwtSecret) {
        this.key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String issue(UUID anonymousId) {
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = anonymousId + ":" + expiresAt;
        return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64(hmac(payload));
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el handoff de visitante", e);
        }
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
