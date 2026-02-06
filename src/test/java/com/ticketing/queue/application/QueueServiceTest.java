package com.ticketing.queue.application;

import com.ticketing.IntegrationTestBase;
import com.ticketing.queue.application.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
