package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.form.service.SymptomMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integracion 3 (HU-14): form publica el evento de la encuesta en un Kafka real y se lee tal como lo
 * consume promotion (SurveyListener usa las claves anonymousId y hasSymptoms del JSON).
 */
@Tag("integration")
@Testcontainers
class SurveyEventKafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Test
    void submittedSurveyReachesTheTopicWithTheContractPromotionExpects() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)));

        HealthSurveyRepository repo = mock(HealthSurveyRepository.class);
        when(repo.save(any(HealthSurvey.class))).thenAnswer(i -> i.getArgument(0));
        QuestionnaireService questionnaires = mock(QuestionnaireService.class);
        SymptomMapper mapper = mock(SymptomMapper.class);
        Questionnaire q = new Questionnaire();
        when(questionnaires.getActiveQuestionnaire()).thenReturn(Optional.of(q));
        when(mapper.hasSymptoms(any(), any())).thenReturn(true);
        UUID anon = UUID.randomUUID();

        new HealthSurveyService(repo, questionnaires, mapper, template)
                .submitSurvey(HealthSurvey.builder().anonymousId(anon).build());
        template.flush();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("survey.submitted"));
            ConsumerRecord<String, String> record = null;
            long deadline = System.currentTimeMillis() + 60_000;
            while (record == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofSeconds(2))) record = r;
            }
            assertNotNull(record, "el evento no llego a Kafka");
            assertEquals(anon.toString(), record.key());
            assertTrue(record.value().contains("\"anonymousId\":\"" + anon + "\""));
            assertTrue(record.value().contains("\"hasSymptoms\":true"));
        }
    }
}
