package com.ticketing.reservation.presentation.dto;

import java.util.UUID;

public record ReservationRequest(
        UUID eventId,
        UUID seatId
) {}
