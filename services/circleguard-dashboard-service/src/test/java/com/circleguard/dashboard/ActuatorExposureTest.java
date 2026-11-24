package com.circleguard.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-24: application.yml importa resilience.yml y la lista de endpoints del archivo
 * importado reemplaza a la de application.yml. Si falta prometheus, Prometheus recibe
 * 404 y dashboard figura caido en las alertas.
 */
class ActuatorExposureTest {

    // Se leen los archivos de produccion: src/test/resources tiene su propio application.yml.

    private static final String KEY = "management.endpoints.web.exposure.include";

    @Test
    void ambosArchivosExponenPrometheusYHealth() throws IOException {
        for (String file : List.of("application.yml", "resilience.yml")) {
            List<String> endpoints = exposed(file);
            assertThat(endpoints).as(file).contains("prometheus", "health");
        }
    }

    private static List<String> exposed(String file) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new FileSystemResource("src/main/resources/" + file));
        Object value = sources.stream().map(s -> s.getProperty(KEY)).filter(v -> v != null).findFirst()
                .orElseThrow(() -> new AssertionError(file + " no define " + KEY));
        return Arrays.stream(value.toString().split(",")).map(String::trim).toList();
    }
}
