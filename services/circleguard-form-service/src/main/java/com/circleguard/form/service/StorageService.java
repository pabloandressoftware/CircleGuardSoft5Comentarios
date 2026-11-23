package com.circleguard.form.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    private final Path root = Paths.get("/tmp/circleguard-uploads");

    public StorageService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String store(MultipartFile file) {
        try {
            String original = file.getOriginalFilename();
            String safeName = original == null ? "file" : Paths.get(original).getFileName().toString();
            if (safeName.isBlank() || safeName.contains("..")) {
                safeName = "file";
            }
            String filename = UUID.randomUUID() + "_" + safeName;
            Path target = this.root.resolve(filename).normalize();
            if (!target.startsWith(this.root)) {
                throw new IllegalArgumentException("Nombre de archivo invalido");
            }
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }
}
