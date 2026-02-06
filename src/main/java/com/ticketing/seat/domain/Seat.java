package com.ticketing.seat.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    public Seat(UUID eventId, String seatNumber) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public void reserve() {
        if (this.status == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 예매된 좌석입니다.");
        }
        this.status = SeatStatus.RESERVED;
    }

    public boolean isReserved() {
        return this.status == SeatStatus.RESERVED;
    }
}
