package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class IdentityVaultService {
    private final IdentityMappingRepository repository;

    @Value("${vault.hash-salt:circleguard-default-salt}")
    private String hashSalt;

    /**
     * Maps a real identity to a secure, encrypted anonymous ID.
     * Es idempotente: dos logins concurrentes del mismo usuario no crean dos IDs.
     */
    public UUID getOrCreateAnonymousId(String realIdentity) {
        String hash = computeHash(realIdentity);

        var existing = repository.findByIdentityHash(hash);
        if (existing.isPresent()) {
            return existing.get().getAnonymousId();
        }

        String salt = KeyGenerators.string().generateKey();
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity(realIdentity)
                .identityHash(hash)
                .salt(salt)
                .build();
        try {
            return repository.save(mapping).getAnonymousId();
        } catch (DataIntegrityViolationException e) {
            // Otro hilo inserto el mismo identity_hash primero: se reutiliza.
            return repository.findByIdentityHash(hash)
                    .map(IdentityMapping::getAnonymousId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "No se pudo resolver la identidad anonima"));
        }
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input + hashSalt).getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    public String resolveRealIdentity(UUID anonymousId) {
        return repository.findById(anonymousId)
                .map(IdentityMapping::getRealIdentity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));
    }
}
