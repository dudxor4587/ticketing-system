package com.ticketing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long seatId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Reservation(Long eventId, Long seatId, String userId) {
        this.eventId = eventId;
        this.seatId = seatId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
}
