package com.streamplatform.streamapi.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamplatform.streamapi.dto.CreateStreamRequest;
import com.streamplatform.streamapi.dto.ScheduleStreamRequest;
import com.streamplatform.streamapi.dto.StreamResponse;
import com.streamplatform.streamapi.service.StreamService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/streams")
public class StreamController {

    private final StreamService streamService;

    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @PostMapping
    public ResponseEntity<StreamResponse> createStream(
            @Valid @RequestBody CreateStreamRequest request) {

        return ResponseEntity.ok(streamService.createStream(request));
    }

    @PostMapping("/schedule")
    public ResponseEntity<StreamResponse> scheduleStream(
            @Valid @RequestBody ScheduleStreamRequest request) {

        return ResponseEntity.ok(streamService.scheduleStream(request));
    }

    @GetMapping
    public ResponseEntity<List<StreamResponse>> getMyStreams() {

        return ResponseEntity.ok(streamService.getMyStreams());
    }

    @GetMapping("/public")
    public ResponseEntity<List<StreamResponse>> getPublicStreams() {

        return ResponseEntity.ok(streamService.getPublicStreams());
    }

    @GetMapping("/scheduled")
    public ResponseEntity<List<StreamResponse>> getPublicScheduledStreams() {

        return ResponseEntity.ok(streamService.getPublicScheduledStreams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamResponse> getStreamById(
            @PathVariable Long id) {

        return ResponseEntity.ok(streamService.getStreamById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StreamResponse> updateStream(
            @PathVariable Long id,
            @Valid @RequestBody CreateStreamRequest request) {

        return ResponseEntity.ok(streamService.updateStream(id, request));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<StreamResponse> cancelScheduledStream(
            @PathVariable Long id) {

        return ResponseEntity.ok(streamService.cancelScheduledStream(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStream(
            @PathVariable Long id) {

        streamService.deleteStream(id);

        return ResponseEntity.noContent().build();
    }
}