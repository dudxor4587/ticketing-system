package com.ticketing.seat.application.dto;

import com.ticketing.seat.domain.Seat;
import com.ticketing.seat.domain.SeatStatus;

import java.util.UUID;

public record SeatResponse(
        UUID id,
        String seatNumber,
        SeatStatus status
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }
}
