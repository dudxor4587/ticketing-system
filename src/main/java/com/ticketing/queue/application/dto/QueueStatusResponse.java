package com.ticketing.queue.application.dto;

public record QueueStatusResponse(
        QueueStatus status,
        Long rank,
        Long aheadCount
) {}
