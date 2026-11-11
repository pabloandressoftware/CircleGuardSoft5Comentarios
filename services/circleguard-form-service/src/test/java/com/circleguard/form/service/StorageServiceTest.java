package com.circleguard.form.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    @Test
    void storesFileWithUniquePrefixedName() {
        String name = new StorageService().store(new MockMultipartFile("file", "cert.pdf", "application/pdf", "x".getBytes()));
        assertTrue(name.endsWith("_cert.pdf"));
    }
}
