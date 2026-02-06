package com.ticketing.controller;

import com.ticketing.dto.QueueEnterResponse;
import com.ticketing.dto.QueueStatusResponse;
import com.ticketing.dto.TokenResponse;
import com.ticketing.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/enter")
    public ResponseEntity<QueueEnterResponse> enter(
            @RequestParam UUID eventId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        QueueEnterResponse response = queueService.enter(eventId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> getStatus(
            @RequestParam UUID eventId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        QueueStatusResponse response = queueService.getStatus(eventId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> acquireToken(
            @RequestParam UUID eventId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        TokenResponse response = queueService.acquireToken(eventId, userId);
        return ResponseEntity.ok(response);
    }
}
