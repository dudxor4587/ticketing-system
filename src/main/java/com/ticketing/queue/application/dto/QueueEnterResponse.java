package com.ticketing.queue.application.dto;

import java.util.UUID;

public record QueueEnterResponse(
        UUID eventId,
        UUID userId,
        Long rank
) {}
