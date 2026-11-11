package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integracion 2 (HU-14): form contra un PostgreSQL real.
 * Las respuestas se guardan como JSONB (H2 no lo soporta) y la consulta de pendientes filtra bien.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FormPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw")) // datos en RAM: el disco lento no cuenta
            .withCommand("postgres", "-c", "fsync=off")
            .withStartupTimeout(java.time.Duration.ofMinutes(5))
            .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw")) // datos en memoria: el disco lento no cuenta
            .withCommand("postgres", "-c", "fsync=off")
            .withStartupTimeout(java.time.Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    HealthSurveyRepository repository;

    @Test
    void jsonResponsesRoundTripAndPendingQueryFiltersByStatus() {
        HealthSurvey pending = repository.save(HealthSurvey.builder().anonymousId(UUID.randomUUID())
                .responses(Map.of("fever", true, "cough", false)).attachmentPath("cert.pdf")
                .validationStatus(ValidationStatus.PENDING).build());
        repository.save(HealthSurvey.builder().anonymousId(UUID.randomUUID())
                .attachmentPath("otro.pdf").validationStatus(ValidationStatus.APPROVED).build());
        repository.saveAndFlush(HealthSurvey.builder().anonymousId(UUID.randomUUID()).build()); // sin adjunto

        List<HealthSurvey> found = repository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING);

        assertEquals(1, found.size());
        assertEquals(pending.getId(), found.get(0).getId());
        assertEquals(true, found.get(0).getResponses().get("fever"));
    }
}
