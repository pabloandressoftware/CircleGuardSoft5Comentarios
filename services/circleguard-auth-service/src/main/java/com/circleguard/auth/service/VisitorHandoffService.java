package com.circleguard.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Verifica el comprobante firmado por identity-service para el handoff de
 * visitantes. Sin este comprobante, /visitor/handoff permitiria a cualquiera
 * mintear un JWT para el anonymousId de otra persona.
 */
@Service
public class VisitorHandoffService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public VisitorHandoffService(@Value("${jwt.secret}") String jwtSecret) {
        this.key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public boolean verify(UUID anonymousId, String handoffToken) {
        if (anonymousId == null || handoffToken == null) {
            return false;
        }
        int separator = handoffToken.lastIndexOf('.');
        if (separator <= 0 || separator == handoffToken.length() - 1) {
            return false;
        }
        try {
            String payload = new String(
                    Base64.getUrlDecoder().decode(handoffToken.substring(0, separator)),
                    StandardCharsets.UTF_8);
            byte[] providedSignature = Base64.getUrlDecoder().decode(handoffToken.substring(separator + 1));
            if (!MessageDigest.isEqual(hmac(payload), providedSignature)) {
                return false;
            }

            String[] parts = payload.split(":");
            if (parts.length != 2) {
                return false;
            }
            if (!UUID.fromString(parts[0]).equals(anonymousId)) {
                return false;
            }
            long expiresAt = Long.parseLong(parts[1]);
            return Instant.now().getEpochSecond() <= expiresAt;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo verificar el handoff de visitante", e);
        }
    }
}
