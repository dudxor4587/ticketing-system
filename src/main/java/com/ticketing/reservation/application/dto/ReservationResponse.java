package com.ticketing.reservation.application.dto;

import com.ticketing.reservation.domain.Reservation;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID eventId,
        UUID seatId,
        UUID userId,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getSeatId(),
                reservation.getUserId(),
                reservation.getCreatedAt()
        );
    }
}
