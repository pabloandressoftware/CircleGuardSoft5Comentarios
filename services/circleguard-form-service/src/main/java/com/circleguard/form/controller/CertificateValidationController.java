package com.circleguard.form.controller;

import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.security.JwtAuthorizer;
import com.circleguard.form.service.HealthSurveyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CertificateValidationController {

    private final HealthSurveyService surveyService;
    private final JwtAuthorizer jwtAuthorizer;

    @GetMapping("/pending")
    public ResponseEntity<?> getPending(HttpServletRequest request) {
        jwtAuthorizer.requirePermission(request, "certificate:validate");
        return ResponseEntity.ok(surveyService.getPendingSurveys());
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(
            @PathVariable UUID id,
            @RequestParam ValidationStatus status,
            @RequestParam(required = false) UUID adminId,
            HttpServletRequest request) {
        jwtAuthorizer.requirePermission(request, "certificate:validate");
        UUID actor = adminId;
        if (actor == null) {
            String subject = jwtAuthorizer.subject(request);
            actor = subject != null ? UUID.fromString(subject) : null;
        }
        surveyService.validateSurvey(id, status, actor);
        return ResponseEntity.ok().build();
    }
}
