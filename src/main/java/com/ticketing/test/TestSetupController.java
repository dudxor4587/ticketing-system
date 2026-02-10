package com.ticketing.test;

import com.ticketing.seat.domain.Seat;
import com.ticketing.seat.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Profile("load-test")
public class TestSetupController {

    private final SeatRepository seatRepository;

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setup(@RequestParam(defaultValue = "100") int seatCount) {
        UUID eventId = UUID.randomUUID();

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            seats.add(new Seat(eventId, "A" + i));
        }
        seatRepository.saveAll(seats);

        List<UUID> seatIds = seats.stream().map(Seat::getId).toList();

        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "seatIds", seatIds,
                "seatCount", seatCount
        ));
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Void> cleanup() {
        seatRepository.deleteAll();
        return ResponseEntity.ok().build();
    }
}
