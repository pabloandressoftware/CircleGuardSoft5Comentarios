package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class HealthSurveyServiceTest {

    private final HealthSurveyRepository repo = mock(HealthSurveyRepository.class);
    private final QuestionnaireService questionnaires = mock(QuestionnaireService.class);
    private final SymptomMapper mapper = mock(SymptomMapper.class);
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final HealthSurveyService service = new HealthSurveyService(repo, questionnaires, mapper, kafka);

    @Test
    void submitSavesFillsLegacyFieldsMarksAttachmentPendingAndPublishesEvent() {
        UUID anon = UUID.randomUUID();
        Questionnaire q = new Questionnaire();
        when(questionnaires.getActiveQuestionnaire()).thenReturn(Optional.of(q));
        when(mapper.hasSymptoms(any(), eq(q))).thenReturn(true);
        when(repo.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anon).attachmentPath("f.pdf").build();

        HealthSurvey saved = service.submitSurvey(survey);

        assertTrue(saved.getHasFever());
        assertTrue(saved.getHasCough());
        assertEquals(ValidationStatus.PENDING, saved.getValidationStatus());
        verify(kafka).send(eq("survey.submitted"), eq(anon.toString()), any());
    }

    @Test
    void submitWithoutActiveQuestionnaireReportsNoSymptoms() {
        when(questionnaires.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repo.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey saved = service.submitSurvey(HealthSurvey.builder().anonymousId(UUID.randomUUID()).build());

        assertFalse(saved.getHasFever());
        assertNull(saved.getValidationStatus());
    }

    @Test
    void approvingACertificateEmitsEventButRejectingDoesNot() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(UUID.randomUUID()).build();
        when(repo.findById(id)).thenReturn(Optional.of(survey));

        service.validateSurvey(id, ValidationStatus.REJECTED, admin);
        verify(kafka, never()).send(eq("certificate.validated"), any(), any());

        service.validateSurvey(id, ValidationStatus.APPROVED, admin);
        verify(kafka).send(eq("certificate.validated"), any(), any());
        assertEquals(admin, survey.getValidatedBy());
    }

    @Test
    void validatingUnknownSurveyFails() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.validateSurvey(UUID.randomUUID(), ValidationStatus.APPROVED, UUID.randomUUID()));
    }

    @Test
    void pendingSurveysComeFromRepository() {
        when(repo.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING)).thenReturn(List.of(new HealthSurvey()));
        assertEquals(1, service.getPendingSurveys().size());
    }
}
