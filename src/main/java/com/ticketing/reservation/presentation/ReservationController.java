package com.ticketing.reservation.presentation;

import com.ticketing.reservation.application.ReservationService;
import com.ticketing.reservation.application.dto.ReservationResponse;
import com.ticketing.reservation.presentation.dto.ReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            @RequestBody ReservationRequest request,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        ReservationResponse response = reservationService.reserve(
                request.eventId(),
                request.seatId(),
                userId
        );
        return ResponseEntity.ok(response);
    }
}
