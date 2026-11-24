package com.circleguard.auth.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityClientTest {

    @Test
    void mapsRealIdentityToAnonymousIdOverHttp() throws Exception {
        UUID expected = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/identities/map", ex -> {
            byte[] body = ("{\"anonymousId\":\"" + expected + "\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            // la barra final se normaliza
            IdentityClient client = new IdentityClient("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    Duration.ofMillis(200), Duration.ofMillis(250), new RestTemplateBuilder());
            assertEquals(expected, client.getAnonymousId("ana@icesi.edu.co"));
        } finally {
            server.stop(0);
        }
    }
}
