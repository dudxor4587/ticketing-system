package com.ticketing.dto;

public record QueueStatusResponse(
        QueueStatus status,
        Long rank,
        Long aheadCount
) {}
