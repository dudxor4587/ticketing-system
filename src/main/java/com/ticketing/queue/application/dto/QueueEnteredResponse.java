package com.ticketing.queue.application.dto;

public record QueueEnteredResponse(
        QueueStatus status
) implements QueueResponse {
}
