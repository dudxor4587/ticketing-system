package com.ticketing.reservation.application;

import com.ticketing.queue.application.QueueService;
import com.ticketing.reservation.application.dto.ReservationResponse;
import com.ticketing.reservation.domain.Reservation;
import com.ticketing.reservation.domain.repository.ReservationRepository;
import com.ticketing.seat.domain.Seat;
import com.ticketing.seat.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;

    private static final String LOCK_KEY = "lock:seat:%s:%s";
    private static final String SEAT_COUNT_KEY = "seat:count:%s";

    @Transactional
    public ReservationResponse reserve(UUID eventId, UUID seatId, UUID userId) {
        // 1. 토큰 확인
        if (!queueService.hasToken(eventId, userId)) {
            throw new IllegalStateException("입장 토큰이 없습니다.");
        }

        // 2. 분산 락 획득
        String lockKey = String.format(LOCK_KEY, eventId, seatId);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);

            if (!acquired) {
                throw new IllegalStateException("다른 사용자가 선택 중입니다.");
            }

            // 3. 좌석 상태 확인 (DB 락)
            Seat seat = seatRepository.findByIdForUpdate(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

            if (seat.isReserved()) {
                throw new IllegalStateException("이미 예매된 좌석입니다.");
            }

            // 4. 좌석 예매 처리
            seat.reserve();

            // 5. 예매 정보 저장
            Reservation reservation = new Reservation(eventId, seatId, userId);
            reservationRepository.save(reservation);

            // 6. Redis 잔여 좌석 감소
            String seatCountKey = String.format(SEAT_COUNT_KEY, eventId);
            redisTemplate.opsForValue().decrement(seatCountKey);

            // 7. 토큰 반환
            queueService.releaseToken(eventId, userId);

            return ReservationResponse.from(reservation);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
