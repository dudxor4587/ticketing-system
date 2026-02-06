package com.ticketing.seat.presentation;

import com.ticketing.seat.application.SeatService;
import com.ticketing.seat.application.dto.SeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/{eventId}/seats")
    public ResponseEntity<List<SeatResponse>> getSeats(@PathVariable UUID eventId) {
        List<SeatResponse> seats = seatService.getSeats(eventId);
        return ResponseEntity.ok(seats);
    }
}
