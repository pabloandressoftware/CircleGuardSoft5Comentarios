package com.circleguard.auth.controller;

import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QrTokenControllerTest {

    @Test
    void returnsTokenAndExpirationInSeconds() {
        QrTokenService qr = mock(QrTokenService.class);
        UUID id = UUID.randomUUID();
        when(qr.generateQrToken(id)).thenReturn("tok");
        when(qr.getExpirationSeconds()).thenReturn(60L);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(id.toString());

        var body = new QrTokenController(qr).generateToken(auth).getBody();

        assertEquals("tok", body.get("qrToken"));
        assertEquals("60", body.get("expiresIn"));
    }
}
