package com.streamplatform.streamapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamplatform.streamapi.dto.CreatorAnalyticsResponse;
import com.streamplatform.streamapi.service.AnalyticsService;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/creator")
    public ResponseEntity<CreatorAnalyticsResponse> getCreatorAnalytics() {
        return ResponseEntity.ok(analyticsService.getCreatorAnalytics());
    }
}
