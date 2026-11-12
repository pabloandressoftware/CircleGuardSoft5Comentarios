package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdentityVaultServiceTest {

    private final IdentityMappingRepository repo = mock(IdentityMappingRepository.class);
    private final IdentityVaultService service = new IdentityVaultService(repo);

    @Test
    void returnsExistingAnonymousIdWithoutCreatingAnother() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdentityHash(anyString()))
                .thenReturn(Optional.of(IdentityMapping.builder().anonymousId(id).build()));

        assertEquals(id, service.getOrCreateAnonymousId("ana@icesi.edu.co"));
        verify(repo, never()).save(any());
    }

    @Test
    void createsMappingWithHashAndSaltWhenIdentityIsNew() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repo.save(any(IdentityMapping.class))).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            assertEquals("ana@icesi.edu.co", m.getRealIdentity());
            assertEquals(64, m.getIdentityHash().length()); // SHA-256 en hexadecimal
            assertNotNull(m.getSalt());
            m.setAnonymousId(id);
            return m;
        });

        assertEquals(id, service.getOrCreateAnonymousId("ana@icesi.edu.co"));
    }

    @Test
    void resolvesKnownAndRejectsUnknownAnonymousId() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(IdentityMapping.builder().realIdentity("ana").build()));
        when(repo.findById(not(eq(id)))).thenReturn(Optional.empty());

        assertEquals("ana", service.resolveRealIdentity(id));
        assertThrows(ResponseStatusException.class, () -> service.resolveRealIdentity(UUID.randomUUID()));
    }
}
