package com.circleguard.form.controller;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.security.JwtAuthorizer;
import com.circleguard.form.service.QuestionnaireService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questionnaires")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuestionnaireController {
    private final QuestionnaireService service;
    private final JwtAuthorizer jwtAuthorizer;

    @GetMapping
    public ResponseEntity<List<Questionnaire>> getAll() {
        return ResponseEntity.ok(service.getAllQuestionnaires());
    }

    @GetMapping("/active")
    public ResponseEntity<Questionnaire> getActive() {
        return service.getActiveQuestionnaire()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Questionnaire> create(@RequestBody Questionnaire questionnaire, HttpServletRequest request) {
        jwtAuthorizer.requirePermission(request, "questionnaire:manage");
        return ResponseEntity.ok(service.saveQuestionnaire(questionnaire));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id, HttpServletRequest request) {
        jwtAuthorizer.requirePermission(request, "questionnaire:manage");
        service.activateQuestionnaire(id);
        return ResponseEntity.ok().build();
    }
}
