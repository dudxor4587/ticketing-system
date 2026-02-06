package com.ticketing.service;

import com.ticketing.config.TicketingProperties;
import com.ticketing.dto.QueueEnterResponse;
import com.ticketing.dto.QueueStatus;
import com.ticketing.dto.QueueStatusResponse;
import com.ticketing.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final StringRedisTemplate redisTemplate;
    private final TicketingProperties properties;

    private static final String QUEUE_KEY = "queue:%s";
    private static final String TOKEN_KEY = "token:%s:%s";
    private static final String TOKEN_COUNT_KEY = "token:count:%s";

    public QueueEnterResponse enter(UUID eventId, UUID userId) {
        String queueKey = String.format(QUEUE_KEY, eventId);
        String userIdStr = userId.toString();
        double score = System.currentTimeMillis();

        // ZADD queue:{eventId} {timestamp} {userId}
        redisTemplate.opsForZSet().add(queueKey, userIdStr, score);

        // ZRANK로 순번 조회
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userIdStr);

        return new QueueEnterResponse(eventId, userId, rank);
    }

    public QueueStatusResponse getStatus(UUID eventId, UUID userId) {
        String tokenKey = String.format(TOKEN_KEY, eventId, userId);
        String queueKey = String.format(QUEUE_KEY, eventId);
        String countKey = String.format(TOKEN_COUNT_KEY, eventId);

        // 이미 토큰 있는지 확인
        if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))) {
            // 토큰 TTL 갱신
            redisTemplate.expire(tokenKey, properties.getTokenTtl(), TimeUnit.SECONDS);
            return new QueueStatusResponse(QueueStatus.ENTERED, null, null);
        }

        // 순위 조회
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        if (rank == null) {
            return new QueueStatusResponse(QueueStatus.WAITING, null, null);
        }

        // 현재 입장 인원 조회
        String countStr = redisTemplate.opsForValue().get(countKey);
        int tokenCount = countStr != null ? Integer.parseInt(countStr) : 0;

        // 남은 자리 계산
        int remaining = properties.getMaxConcurrent() - tokenCount;

        // 상태 결정
        QueueStatus status = rank < remaining ? QueueStatus.READY : QueueStatus.WAITING;

        return new QueueStatusResponse(status, rank, rank);
    }

    public TokenResponse acquireToken(UUID eventId, UUID userId) {
        String tokenKey = String.format(TOKEN_KEY, eventId, userId);
        String countKey = String.format(TOKEN_COUNT_KEY, eventId);
        String queueKey = String.format(QUEUE_KEY, eventId);

        String luaScript = """
                local tokenKey = KEYS[1]
                local countKey = KEYS[2]
                local queueKey = KEYS[3]
                local userId = ARGV[1]
                local maxConcurrent = tonumber(ARGV[2])
                local ttl = tonumber(ARGV[3])

                -- 이미 토큰 있으면 성공
                if redis.call('EXISTS', tokenKey) == 1 then
                    return 1
                end

                -- 내 순번 확인
                local rank = redis.call('ZRANK', queueKey, userId)
                if rank == false then
                    return -1
                end

                -- 남은 자리 계산
                local current = tonumber(redis.call('GET', countKey) or 0)
                local remaining = maxConcurrent - current

                -- 내 순번 < 남은 자리 → 토큰 발급
                if rank < remaining then
                    redis.call('SET', tokenKey, 1, 'EX', ttl)
                    redis.call('INCR', countKey)
                    redis.call('ZREM', queueKey, userId)
                    return 1
                end

                return 0
                """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);

        Long result = redisTemplate.execute(
                script,
                List.of(tokenKey, countKey, queueKey),
                userId.toString(),
                String.valueOf(properties.getMaxConcurrent()),
                String.valueOf(properties.getTokenTtl())
        );

        if (result == null || result == 0) {
            return new TokenResponse(false, "아직 입장 순서가 아닙니다.");
        } else if (result == -1) {
            return new TokenResponse(false, "대기열에 등록되지 않았습니다.");
        }

        return new TokenResponse(true, "입장 완료");
    }

    public boolean hasToken(UUID eventId, UUID userId) {
        String tokenKey = String.format(TOKEN_KEY, eventId, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
    }

    public void releaseToken(UUID eventId, UUID userId) {
        String tokenKey = String.format(TOKEN_KEY, eventId, userId);
        String countKey = String.format(TOKEN_COUNT_KEY, eventId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey))) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForValue().decrement(countKey);
        }
    }
}
