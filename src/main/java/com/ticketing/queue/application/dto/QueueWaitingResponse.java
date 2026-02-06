package com.ticketing.queue.application.dto;

public record QueueWaitingResponse(
        QueueStatus status,
        Long rank,
        Long aheadCount
) implements QueueResponse {
}
