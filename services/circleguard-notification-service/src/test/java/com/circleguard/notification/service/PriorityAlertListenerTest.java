package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class PriorityAlertListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TemplateService templateService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PriorityAlertListener priorityAlertListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(priorityAlertListener, "authApiUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(priorityAlertListener, "internalApiKey", "test-internal-key");
        ReflectionTestUtils.setField(priorityAlertListener, "restTemplate", restTemplate);
    }

    @Test
    void testHandlePriorityAlert_Success() throws Exception {
        String message = "{\"eventType\":\"CONFIRMED_CASE\",\"affectedCount\":1}";
        Map<String, Object> payload = Map.of("eventType", "CONFIRMED_CASE", "affectedCount", 1);
        
        when(objectMapper.readValue(eq(message), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
            .thenReturn(payload);

        List<Map<String, String>> mockAdmins = List.of(
            Map.of("email", "admin@university.edu", "username", "admin1")
        );
        
        when(restTemplate.exchange(
                eq("http://localhost:8080/api/v1/users/permissions/alert:receive_priority"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
            .thenReturn(new ResponseEntity<>(mockAdmins, HttpStatus.OK));

        priorityAlertListener.handlePriorityAlert(message);

        verify(templateService, times(1)).generateEmailContent("CONFIRMED_CASE", "admin1");
    }

    @Test
    void testHandlePriorityAlert_NoAdmins() throws Exception {
        String message = "{\"eventType\":\"CONFIRMED_CASE\",\"affectedCount\":1}";
        Map<String, Object> payload = Map.of("eventType", "CONFIRMED_CASE", "affectedCount", 1);
        
        when(objectMapper.readValue(eq(message), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
            .thenReturn(payload);

        when(restTemplate.exchange(
                eq("http://localhost:8080/api/v1/users/permissions/alert:receive_priority"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(List.class)))
            .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        priorityAlertListener.handlePriorityAlert(message);

        verify(templateService, never()).generateEmailContent(anyString(), anyString());
    }
}
