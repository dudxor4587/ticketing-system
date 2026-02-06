package com.ticketing.queue.application;

import com.ticketing.IntegrationTestBase;
import com.ticketing.queue.application.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueServiceTest extends IntegrationTestBase {

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        // Redis 초기화 - 테스트 관련 키만 삭제
        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("대기열 진입 시 순위를 반환한다")
    void enter_returnsRank() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        QueueEnterResponse response1 = queueService.enter(eventId, user1);
        QueueEnterResponse response2 = queueService.enter(eventId, user2);

        assertThat(response1.rank()).isEqualTo(0L);
        assertThat(response2.rank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기열에 없는 사용자 상태 조회 시 예외 발생")
    void getStatus_notInQueue_throwsException() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> queueService.getStatus(eventId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("대기열에 등록되지 않았습니다.");
    }

    @Test
    @DisplayName("입장 가능 시 READY 상태를 반환한다")
    void getStatus_whenCanEnter_returnsReady() {
        UUID userId = UUID.randomUUID();
        queueService.enter(eventId, userId);

        QueueResponse response = queueService.getStatus(eventId, userId);

        assertThat(response).isInstanceOf(QueueWaitingResponse.class);
        QueueWaitingResponse waitingResponse = (QueueWaitingResponse) response;
        assertThat(waitingResponse.status()).isEqualTo(QueueStatus.READY);
        assertThat(waitingResponse.rank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("토큰 발급 성공")
    void acquireToken_success() {
        UUID userId = UUID.randomUUID();
        queueService.enter(eventId, userId);

        TokenResponse response = queueService.acquireToken(eventId, userId);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("입장 완료");
    }

    @Test
    @DisplayName("토큰 발급 후 상태는 ENTERED")
    void getStatus_afterToken_returnsEntered() {
        UUID userId = UUID.randomUUID();
        queueService.enter(eventId, userId);
        queueService.acquireToken(eventId, userId);

        QueueResponse response = queueService.getStatus(eventId, userId);

        assertThat(response).isInstanceOf(QueueEnteredResponse.class);
        assertThat(((QueueEnteredResponse) response).status()).isEqualTo(QueueStatus.ENTERED);
    }

    @Test
    @DisplayName("대기열에 없는 사용자 토큰 발급 실패")
    void acquireToken_notInQueue_fails() {
        UUID userId = UUID.randomUUID();

        TokenResponse response = queueService.acquireToken(eventId, userId);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("대기열에 등록되지 않았습니다.");
    }

    @Test
    @DisplayName("토큰 반환 후 hasToken은 false")
    void releaseToken_removesToken() {
        UUID userId = UUID.randomUUID();
        queueService.enter(eventId, userId);
        queueService.acquireToken(eventId, userId);

        assertThat(queueService.hasToken(eventId, userId)).isTrue();

        queueService.releaseToken(eventId, userId);

        assertThat(queueService.hasToken(eventId, userId)).isFalse();
    }

    @Test
    @DisplayName("동시 입장 인원 초과 시 WAITING 상태를 반환한다")
    void getStatus_whenMaxConcurrentExceeded_returnsWaiting() {
        // maxConcurrent = 800, 토큰 카운트를 800으로 설정하면 remaining = 0
        String tokenCountKey = String.format("token:count:%s", eventId);
        redisTemplate.opsForValue().set(tokenCountKey, "800");

        UUID userId = UUID.randomUUID();
        queueService.enter(eventId, userId);

        QueueResponse response = queueService.getStatus(eventId, userId);

        assertThat(response).isInstanceOf(QueueWaitingResponse.class);
        QueueWaitingResponse waitingResponse = (QueueWaitingResponse) response;
        assertThat(waitingResponse.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(waitingResponse.rank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("앞사람이 토큰 발급받으면 뒤사람 순위가 올라간다")
    void getStatus_afterOtherUserAcquiresToken_rankDecreases() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        queueService.enter(eventId, user1);
        queueService.enter(eventId, user2);

        // user2는 rank 1
        QueueWaitingResponse before = (QueueWaitingResponse) queueService.getStatus(eventId, user2);
        assertThat(before.rank()).isEqualTo(1L);

        // user1이 토큰 발급 (대기열에서 제거됨)
        queueService.acquireToken(eventId, user1);

        // user2는 rank 0으로 올라감
        QueueWaitingResponse after = (QueueWaitingResponse) queueService.getStatus(eventId, user2);
        assertThat(after.rank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("입장 가능 인원보다 뒤에 있으면 WAITING")
    void getStatus_whenBehindRemainingSlots_returnsWaiting() {
        // remaining = 2 (maxConcurrent 800 - tokenCount 798)
        String tokenCountKey = String.format("token:count:%s", eventId);
        redisTemplate.opsForValue().set(tokenCountKey, "798");

        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID userId = UUID.randomUUID();
            users.add(userId);
            queueService.enter(eventId, userId);
        }

        // rank 0, 1 → READY (remaining = 2)
        // rank 2, 3, 4 → WAITING
        assertThat(((QueueWaitingResponse) queueService.getStatus(eventId, users.get(0))).status())
                .isEqualTo(QueueStatus.READY);
        assertThat(((QueueWaitingResponse) queueService.getStatus(eventId, users.get(1))).status())
                .isEqualTo(QueueStatus.READY);
        assertThat(((QueueWaitingResponse) queueService.getStatus(eventId, users.get(2))).status())
                .isEqualTo(QueueStatus.WAITING);
        assertThat(((QueueWaitingResponse) queueService.getStatus(eventId, users.get(3))).status())
                .isEqualTo(QueueStatus.WAITING);
    }
}
