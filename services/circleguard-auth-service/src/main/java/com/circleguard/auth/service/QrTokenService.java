package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
public class QrTokenService {
    /** Vigencia minima aceptada. Un valor menor casi siempre es un error de unidades (segundos en vez de ms). */
    static final long MIN_EXPIRATION_MILLIS = 1_000;

    private final Key key;
    private final long expiration;

    /**
     * @param expiration vigencia del QR en MILISEGUNDOS (qr.expiration / QR_EXPIRATION). 60000 = 60 s.
     */
    public QrTokenService(@Value("${qr.secret}") String secret,
                         @Value("${qr.expiration:60000}") long expiration) {
        if (expiration < MIN_EXPIRATION_MILLIS) {
            throw new IllegalArgumentException(
                "qr.expiration se mide en milisegundos y debe ser >= " + MIN_EXPIRATION_MILLIS
                + " (recibido: " + expiration + "). Para 60 segundos usa 60000.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    public String generateQrToken(UUID anonymousId) {
        return Jwts.builder()
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Vigencia en segundos, la unidad que usa el cliente para su cuenta regresiva. */
    public long getExpirationSeconds() {
        return expiration / 1000;
    }
}
