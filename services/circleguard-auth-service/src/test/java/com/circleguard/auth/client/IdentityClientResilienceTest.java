package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-22 · Criterio 3: con identity apagado se abre el circuito y el login recibe un error
 * controlado en menos de 1 s. Usa la configuracion real de resilience.yml y las anotaciones
 * de Resilience4j (contexto de Spring con AOP), no una simulacion.
 */
@SpringBootTest(
        classes = IdentityClientResilienceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=classpath:resilience.yml",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
class IdentityClientResilienceTest {

    /** Solo IdentityClient y la autoconfiguracion (Resilience4j, AOP, RestTemplateBuilder). */
    @Configuration
    @EnableAutoConfiguration
    @Import(IdentityClient.class)
    static class TestConfig {
    }

    /** Un puerto sin nadie escuchando: identity-service "apagado" (conexion rechazada). */
    private static final int IDENTITY_DOWN_PORT = freePort();

    @DynamicPropertySource
    static void identityUrl(DynamicPropertyRegistry registry) {
        registry.add("circleguard.identity-service.url", () -> "http://127.0.0.1:" + IDENTITY_DOWN_PORT);
    }

    @Autowired
    private IdentityClient identityClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;

    @BeforeEach
    void closeCircuit() {
        // Calentamiento: la primera llamada de una JVM nueva paga la carga de clases (RestTemplate,
        // Jackson, proxies AOP) y no es representativa de un login real. Se descarta y se reinicia el circuito.
        try {
            identityClient.getAnonymousId("warm-up");
        } catch (IdentityUnavailableException ignored) {
            // esperado: identity esta apagado
        }
        breaker = circuitBreakerRegistry.circuitBreaker(IdentityClient.RESILIENCE_INSTANCE);
        breaker.reset();
    }

    @Test
    void identityDownGivesControlledErrorInLessThanOneSecond() {
        long elapsedMs = timeMs(() -> assertThrows(IdentityUnavailableException.class,
                () -> identityClient.getAnonymousId("ana@icesi.edu.co")));

        assertTrue(elapsedMs < 1000, "El error controlado tardo " + elapsedMs + " ms (debe ser < 1000 ms)");
        // Retry: el intento original y el reintento pasaron por el circuito y fallaron
        assertEquals(2, breaker.getMetrics().getNumberOfFailedCalls());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        System.out.printf("HU-22 evidencia: identity apagado -> error controlado en %d ms%n", elapsedMs);
    }

    @Test
    void circuitOpensAndThenAnswersImmediatelyWithoutCallingIdentity() {
        // 3 logins con identity apagado = hasta 6 llamadas fallidas: supera el minimo (5) y el 50 %
        for (int i = 0; i < 3; i++) {
            assertThrows(IdentityUnavailableException.class, () -> identityClient.getAnonymousId("ana@icesi.edu.co"));
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        long rejectedBefore = breaker.getMetrics().getNumberOfNotPermittedCalls();
        IdentityUnavailableException[] error = new IdentityUnavailableException[1];
        long elapsedMs = timeMs(() -> error[0] = assertThrows(IdentityUnavailableException.class,
                () -> identityClient.getAnonymousId("ana@icesi.edu.co")));

        // El circuito rechazo la llamada sin tocar la red, y el Retry no la repitio
        assertInstanceOf(CallNotPermittedException.class, error[0].getCause());
        assertEquals(rejectedBefore + 1, breaker.getMetrics().getNumberOfNotPermittedCalls());
        assertTrue(elapsedMs < 100, "Con el circuito abierto tardo " + elapsedMs + " ms (debe ser casi inmediato)");
        System.out.printf("HU-22 evidencia: circuito %s -> respuesta en %d ms sin llamar a identity%n",
                breaker.getState(), elapsedMs);
    }

    private static long timeMs(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
