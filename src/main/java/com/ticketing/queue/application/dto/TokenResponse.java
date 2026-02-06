package com.ticketing.queue.application.dto;

public record TokenResponse(
        boolean success,
        String message
) {}
