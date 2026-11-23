package com.circleguard.file.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String saveFile(MultipartFile file) {
        String original = file.getOriginalFilename();
        String safeName = original == null ? "file" : Paths.get(original).getFileName().toString();
        if (safeName.isBlank() || safeName.contains("..")) {
            safeName = "file";
        }
        String filename = UUID.randomUUID().toString() + "_" + safeName;
        try {
            Path target = this.root.resolve(filename).normalize();
            if (!target.startsWith(this.root)) {
                throw new IllegalArgumentException("Nombre de archivo invalido");
            }
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    public Resource loadFile(String filename) {
        try {
            Path file = this.root.resolve(filename).normalize();
            if (!file.startsWith(this.root)) {
                throw new IllegalArgumentException("Nombre de archivo invalido");
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Archivo no encontrado");
            }
            return resource;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el archivo", e);
        }
    }
}
