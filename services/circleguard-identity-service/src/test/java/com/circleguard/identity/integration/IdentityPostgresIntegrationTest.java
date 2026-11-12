package com.circleguard.identity.integration;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integracion 1 (HU-14): identity contra un PostgreSQL real.
 * Comprueba que la identidad real se guarda CIFRADA en la base y que se recupera en claro por JPA.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdentityPostgresIntegrationTest {

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
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    IdentityMappingRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void realIdentityIsEncryptedAtRestAndReadableThroughJpa() {
        IdentityMapping saved = repository.saveAndFlush(IdentityMapping.builder()
                .realIdentity("ana@icesi.edu.co").identityHash("hash-it-1").salt("s").build());

        String stored = jdbc.queryForObject(
                "select real_identity_encrypted from identity_mappings where anonymous_id = ?", String.class, saved.getAnonymousId());

        assertNotNull(stored);
        assertNotEquals("ana@icesi.edu.co", stored, "la identidad no debe quedar en claro en la base");
        assertEquals("ana@icesi.edu.co", repository.findByIdentityHash("hash-it-1").orElseThrow().getRealIdentity());
    }
}
