package com.circleguard.dashboard.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-22 · Criterio 2: PromotionClient usa Circuit Breaker y Retry con fallback. Con
 * promotion-service apagado el dashboard recibe el mapa de error sin esperar, y el circuito
 * se abre. Usa la configuracion real de resilience.yml.
 */
@SpringBootTest(
        classes = PromotionClientResilienceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
class PromotionClientResilienceTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(PromotionClient.class)
    static class TestConfig {
    }

    /** Un puerto sin nadie escuchando: promotion-service "apagado". */
    private static final int PROMOTION_DOWN_PORT = freePort();

    @DynamicPropertySource
    static void promotionUrl(DynamicPropertyRegistry registry) {
        registry.add("circleguard.promotion-service.url", () -> "http://127.0.0.1:" + PROMOTION_DOWN_PORT);
    }

    @Autowired
    private PromotionClient promotionClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;

    @BeforeEach
    void closeCircuit() {
        breaker = circuitBreakerRegistry.circuitBreaker(PromotionClient.RESILIENCE_INSTANCE);
        breaker.reset();
    }

    @Test
    void promotionDownReturnsFallbackQuickly() {
        long start = System.nanoTime();
        Map<String, Object> stats = promotionClient.getHealthStats();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals("Service unavailable", stats.get("error"));
        assertTrue(elapsedMs < 1000, "El fallback tardo " + elapsedMs + " ms (debe ser < 1000 ms)");
        assertEquals(2, breaker.getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void departmentFallbackKeepsTheDepartment() {
        Map<String, Object> stats = promotionClient.getHealthStatsByDepartment("ingenieria");

        assertEquals("Service unavailable", stats.get("error"));
        assertEquals("ingenieria", stats.get("department"));
    }

    @Test
    void circuitOpensAndStopsCallingPromotion() {
        for (int i = 0; i < 3; i++) {
            assertEquals("Service unavailable", promotionClient.getHealthStats().get("error"));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        long rejectedBefore = breaker.getMetrics().getNumberOfNotPermittedCalls();
        assertEquals("Service unavailable", promotionClient.getHealthStats().get("error"));
        // Rechazada por el circuito una sola vez: no salio a la red ni se reintento
        assertEquals(rejectedBefore + 1, breaker.getMetrics().getNumberOfNotPermittedCalls());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
