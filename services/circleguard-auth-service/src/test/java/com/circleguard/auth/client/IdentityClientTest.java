package com.circleguard.auth.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityClientTest {

    @Test
    void mapsRealIdentityToAnonymousIdOverHttp() throws Exception {
        UUID expected = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/identities/map", ex -> {
            // HU-21: el login se autentica como servicio con la clave interna.
            if (!"test-internal-key".equals(ex.getRequestHeaders().getFirst("X-Internal-Api-Key"))) {
                ex.sendResponseHeaders(401, -1);
                ex.close();
                return;
            }
            byte[] body = ("{\"anonymousId\":\"" + expected + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            // la barra final se normaliza
            IdentityClient client = new IdentityClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    "test-internal-key");
            assertEquals(expected, client.getAnonymousId("ana@icesi.edu.co"));
        } finally {
            server.stop(0);
        }
    }
}
