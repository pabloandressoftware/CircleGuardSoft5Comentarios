package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente HTTP hacia identity-service.
 *
 * HU-07: la URL sale de IDENTITY_SERVICE_URL (en contenedores identity no esta en localhost).
 * HU-22: timeout + Retry + Circuit Breaker (Resilience4j). Si identity cae, el login recibe
 * IdentityUnavailableException en menos de 1 s; con el circuito abierto, al instante y sin
 * llamar a identity. Los umbrales estan en application.yml (resilience4j.*.instances.identity).
 */
@Slf4j
@Component
public class IdentityClient {

    /** Nombre de la instancia de Circuit Breaker y Retry en application.yml. */
    public static final String RESILIENCE_INSTANCE = "identity";

    private final RestTemplate restTemplate;
    private final String identityUrl;

    public IdentityClient(
            @Value("${circleguard.identity-service.url:http://localhost:8083}") String identityBaseUrl,
            @Value("${circleguard.identity-service.connect-timeout:200ms}") Duration connectTimeout,
            @Value("${circleguard.identity-service.read-timeout:250ms}") Duration readTimeout,
            RestTemplateBuilder restTemplateBuilder) {
        String base = identityBaseUrl.endsWith("/")
                ? identityBaseUrl.substring(0, identityBaseUrl.length() - 1)
                : identityBaseUrl;
        this.identityUrl = base + "/api/v1/identities/map";
        // Sin timeout, una caida de identity deja el login colgado. Peor caso con los valores
        // por defecto: 2 intentos x (200 + 250) ms + 50 ms de espera = 950 ms (< 1 s).
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    @Retry(name = RESILIENCE_INSTANCE, fallbackMethod = "identityUnavailable")
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map<?, ?> response = restTemplate.postForObject(identityUrl, request, Map.class);
        if (response == null || response.get("anonymousId") == null) {
            throw new IllegalStateException("identity-service respondio sin anonymousId");
        }
        return UUID.fromString(response.get("anonymousId").toString());
    }

    /**
     * Fallback de Resilience4j: se ejecuta cuando se agotan los reintentos o el circuito esta
     * abierto (CallNotPermittedException). No inventa un anonymousId: sin identity no se puede
     * emitir un token valido, asi que se informa el error de forma controlada.
     */
    @SuppressWarnings("unused")
    private UUID identityUnavailable(String realIdentity, Throwable cause) {
        log.warn("identity-service no disponible ({}): {}", cause.getClass().getSimpleName(), cause.getMessage());
        throw new IdentityUnavailableException("identity-service no disponible", cause);
    }
}
