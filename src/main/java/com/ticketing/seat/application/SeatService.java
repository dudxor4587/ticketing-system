package com.ticketing.seat.application;

import com.ticketing.seat.application.dto.SeatResponse;
import com.ticketing.seat.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatRepository seatRepository;

    public List<SeatResponse> getSeats(UUID eventId) {
        return seatRepository.findByEventId(eventId)
                .stream()
                .map(SeatResponse::from)
                .toList();
    }
}
