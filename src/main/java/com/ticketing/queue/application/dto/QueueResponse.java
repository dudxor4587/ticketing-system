package com.ticketing.queue.application.dto;

public sealed interface QueueResponse permits QueueWaitingResponse, QueueEnteredResponse {
}
