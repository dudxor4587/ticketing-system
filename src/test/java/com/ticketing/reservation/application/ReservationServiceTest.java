package com.ticketing.reservation.application;

import com.ticketing.IntegrationTestBase;
import com.ticketing.queue.application.QueueService;
import com.ticketing.reservation.application.dto.ReservationResponse;
import com.ticketing.seat.domain.Seat;
import com.ticketing.seat.domain.SeatStatus;
import com.ticketing.seat.domain.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationServiceTest extends IntegrationTestBase {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        // Redis 초기화
        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("토큰 없이 예매 시 예외 발생")
    void reserve_withoutToken_throwsException() {
        UUID userId = UUID.randomUUID();
        Seat seat = seatRepository.save(new Seat(eventId, "A1"));

        assertThatThrownBy(() -> reservationService.reserve(eventId, seat.getId(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("입장 토큰이 없습니다.");
    }

    @Test
    @DisplayName("정상 예매 성공")
    void reserve_success() {
        UUID userId = UUID.randomUUID();
        Seat seat = seatRepository.save(new Seat(eventId, "A1"));

        // 대기열 진입 + 토큰 발급
        queueService.enter(eventId, userId);
        queueService.acquireToken(eventId, userId);

        ReservationResponse response = reservationService.reserve(eventId, seat.getId(), userId);

        assertThat(response.seatId()).isEqualTo(seat.getId());
        assertThat(response.userId()).isEqualTo(userId);

        // 좌석 상태 확인
        Seat updatedSeat = seatRepository.findById(seat.getId()).orElseThrow();
        assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);

        // 토큰 반환 확인
        assertThat(queueService.hasToken(eventId, userId)).isFalse();
    }

    @Test
    @DisplayName("이미 예매된 좌석 예매 시 예외 발생")
    void reserve_alreadyReserved_throwsException() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Seat seat = seatRepository.save(new Seat(eventId, "A1"));

        // user1 예매
        queueService.enter(eventId, user1);
        queueService.acquireToken(eventId, user1);
        reservationService.reserve(eventId, seat.getId(), user1);

        // user2 같은 좌석 예매 시도
        queueService.enter(eventId, user2);
        queueService.acquireToken(eventId, user2);

        assertThatThrownBy(() -> reservationService.reserve(eventId, seat.getId(), user2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 예매된 좌석입니다.");
    }

    @Test
    @DisplayName("동시에 같은 좌석 예매 시 하나만 성공")
    void reserve_concurrent_onlyOneSucceeds() throws InterruptedException {
        Seat seat = seatRepository.save(new Seat(eventId, "A1"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<UUID> userIds = new ArrayList<>();

        // 모든 사용자 토큰 발급
        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            userIds.add(userId);
            queueService.enter(eventId, userId);
            queueService.acquireToken(eventId, userId);
        }

        // 동시 예매 시도
        for (int i = 0; i < threadCount; i++) {
            final UUID userId = userIds.get(i);
            executor.submit(() -> {
                try {
                    reservationService.reserve(eventId, seat.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }
}
