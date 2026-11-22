package com.circleguard.auth.client;

import com.circleguard.auth.security.InternalApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {
    // In a real microservice, this would use Feign or WebClient
    private final RestTemplate restTemplate = new RestTemplate();
    private final String identityUrl;
    private final String internalApiKey;

    // HU-07: en contenedores identity no esta en localhost; se configura con IDENTITY_SERVICE_URL
    // HU-21: /identities/map exige JWT o la clave interna; el login aun no tiene JWT.
    public IdentityClient(
            @Value("${circleguard.identity-service.url:http://localhost:8083}") String identityBaseUrl,
            @Value("${circleguard.internal.api-key:}") String internalApiKey) {
        String base = identityBaseUrl.endsWith("/") ? identityBaseUrl.substring(0, identityBaseUrl.length() - 1) : identityBaseUrl;
        this.identityUrl = base + "/api/v1/identities/map";
        this.internalApiKey = internalApiKey;
    }

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalApiKeyFilter.HEADER, internalApiKey);
        Map response = restTemplate.postForObject(identityUrl, new HttpEntity<>(request, headers), Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }
}
