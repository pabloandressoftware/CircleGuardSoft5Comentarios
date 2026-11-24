package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PushServiceImplTest {
    private MockRestServiceServer server;
    private PushServiceImpl service;
    private AuditLogService audit;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new PushServiceImpl(builder, "https://gotify.example");
        audit = mock(AuditLogService.class);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        ReflectionTestUtils.setField(service, "gotifyToken", "test-token");
    }

    @Test
    void postsMetadataAndRecordsSuccessfulDelivery() {
        server.expect(requestTo("https://gotify.example/message?token=test-token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Alerta"))
            .andExpect(jsonPath("$.extras['client::notification'].building").value("A"))
            .andRespond(withSuccess());
        assertNull(service.sendAsync("anonymous", "Alerta", Map.of("building", "A")).join());
        verify(audit).logDelivery(eq("anonymous"), eq("PUSH"), eq("SUCCESS"), anyString());
        server.verify();
    }

    @Test
    void keepsHttpFailuresVisibleToRetryAndAuditsAttempt() {
        server.expect(requestTo("https://gotify.example/message?token=test-token"))
            .andRespond(withServerError());
        assertThrows(RestClientResponseException.class,
            () -> service.sendAsync("anonymous", "Alerta", Map.of()));
        verify(audit).logDelivery(eq("anonymous"), eq("PUSH"), eq("RETRY"), anyString());
        server.verify();
    }

    @Test
    void mockModeDoesNotCallAnExternalEndpoint() {
        ReflectionTestUtils.setField(service, "gotifyToken", "MOCK_TOKEN");
        assertNull(service.sendAsync("anonymous", "Alerta", Map.of()).join());
        verify(audit).logDelivery(eq("anonymous"), eq("PUSH"), eq("SUCCESS"), anyString());
        server.verify();
    }
}
