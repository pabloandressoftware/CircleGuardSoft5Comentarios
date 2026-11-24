package com.circleguard.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * Cliente HTTP hacia promotion-service.
 *
 * HU-22: timeout + Retry + Circuit Breaker (Resilience4j). Si promotion cae, el dashboard recibe
 * un mapa con "error" (fallback) en vez de quedarse esperando; con el circuito abierto, al
 * instante. Antes el try/catch interno ocultaba los fallos y el circuito nunca los veia.
 */
@Component
@Slf4j
public class PromotionClient {

    /** Nombre de la instancia de Circuit Breaker y Retry en application.yml. */
    public static final String RESILIENCE_INSTANCE = "promotion";

    private final RestTemplate restTemplate;
    private final String promotionServiceUrl;

    public PromotionClient(
            @Value("${circleguard.promotion-service.url:http://localhost:8088}") String promotionServiceUrl,
            @Value("${circleguard.promotion-service.connect-timeout:200ms}") Duration connectTimeout,
            @Value("${circleguard.promotion-service.read-timeout:250ms}") Duration readTimeout,
            RestTemplateBuilder restTemplateBuilder) {
        this.promotionServiceUrl = promotionServiceUrl.endsWith("/")
                ? promotionServiceUrl.substring(0, promotionServiceUrl.length() - 1)
                : promotionServiceUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Retry(name = RESILIENCE_INSTANCE, fallbackMethod = "healthStatsUnavailable")
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public Map<String, Object> getHealthStats() {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats",
                Map.class
        );
    }

    @SuppressWarnings("unchecked")
    @Retry(name = RESILIENCE_INSTANCE, fallbackMethod = "departmentStatsUnavailable")
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats/department/" + department,
                Map.class
        );
    }

    /** Fallback: mismo contrato que antes de HU-22, asi AnalyticsService no cambia. */
    @SuppressWarnings("unused")
    private Map<String, Object> healthStatsUnavailable(Throwable cause) {
        log.warn("promotion-service no disponible para stats ({}): {}", cause.getClass().getSimpleName(), cause.getMessage());
        return Map.of("error", "Service unavailable", "timestamp", new Date());
    }

    @SuppressWarnings("unused")
    private Map<String, Object> departmentStatsUnavailable(String department, Throwable cause) {
        log.warn("promotion-service no disponible para {} ({}): {}", department, cause.getClass().getSimpleName(), cause.getMessage());
        return Map.of("error", "Service unavailable", "department", department, "timestamp", new Date());
    }
}
