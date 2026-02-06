package com.ticketing.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID seatId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Reservation(UUID eventId, UUID seatId, UUID userId) {
        this.eventId = eventId;
        this.seatId = seatId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
}
