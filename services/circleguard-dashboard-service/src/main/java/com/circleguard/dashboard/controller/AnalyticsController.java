package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.security.JwtAuthorizer;
import com.circleguard.dashboard.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final JwtAuthorizer jwtAuthorizer;

    private void authorize(HttpServletRequest request) {
        jwtAuthorizer.requirePermission(request, "dashboard:view");
    }

    @GetMapping("/trends/{locationId}")
    public ResponseEntity<List<Map<String, Object>>> getTrends(@PathVariable UUID locationId,
                                                               HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(analyticsService.getEntryTrends(locationId));
    }

    @GetMapping("/health-board")
    public ResponseEntity<Map<String, Object>> getHealthBoardStats(HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(analyticsService.getGlobalHealthStats());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(analyticsService.getCampusSummary());
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<Map<String, Object>> getDepartmentStats(@PathVariable String department,
                                                                  HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(analyticsService.getDepartmentStats(department));
    }

    @GetMapping("/time-series")
    public ResponseEntity<List<Map<String, Object>>> getTimeSeries(
            @RequestParam(defaultValue = "hourly") String period,
            @RequestParam(defaultValue = "24") int limit,
            HttpServletRequest request) {
        authorize(request);
        return ResponseEntity.ok(analyticsService.getTimeSeries(period, limit));
    }
}
