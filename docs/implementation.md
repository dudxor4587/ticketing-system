# 구현 상세

아키텍처 문서의 설계를 구체적으로 구현하기 위한 상세 내용.

---

## Redis 키 구조

```
queue:{eventId}              # Sorted Set - 대기열
token:{eventId}:{userId}     # String - 입장 토큰 (TTL)
token:count:{eventId}        # String - 현재 입장 인원 수
seat:count:{eventId}         # String - 잔여 좌석 수
lock:seat:{eventId}:{seatId} # Redisson Lock - 좌석 분산 락
```

---

## 대기열 상세 흐름

### 1. 대기열 진입

```
ZADD queue:{eventId} {timestamp} {userId}
```

- timestamp를 score로 사용하여 선착순 정렬
- 클라이언트에게 대기 순번 반환

### 2. 대기 순번 조회 (Polling)

```
ZRANK queue:{eventId} {userId}
```

- 0-based index 반환
- 사용자에게 "내 앞에 N명" 표시용

**Polling 응답 구조**

```json
{
    "status": "waiting | ready | entered",
    "rank": 1234,
    "aheadCount": 500
}
```

| status | 의미 |
|--------|------|
| `waiting` | 대기 중 (토큰 여유 없음) |
| `ready` | 입장 가능 (토큰 획득 시도 가능) |
| `entered` | 이미 입장함 |

**status 결정 로직**

```java
// 이미 토큰 보유 중인지 확인
if (hasToken(eventId, userId)) {
    return "entered";
}

// 내 순번 조회
Long rank = redis.zrank("queue:" + eventId, visitorId); // 0-based

// 남은 자리 계산
int tokenCount = redis.get("token:count:" + eventId);
int maxConcurrent = 800;  // 설정값
int remaining = maxConcurrent - tokenCount;

// 내 순번 < 남은 자리 → ready
if (rank < remaining) {
    return "ready";
} else {
    return "waiting";
}
```

**토큰 개수 확인 방법**

```
# 방법 1: 별도 카운터 관리 (권장)
token:count:{eventId}  # 현재 토큰 개수

# 방법 2: 키 개수 카운트 (비권장, O(N))
KEYS token:{eventId}:*
```

---

### 3. 입장 (토큰 획득)

**status가 `ready`일 때 토큰 획득 시도**

```
# 토큰 획득 시도 (원자적 처리 필요 - Lua Script 권장)
SETNX token:{eventId}:{userId} 1
EXPIRE token:{eventId}:{userId} 300  # 5분 TTL

# 성공 시
INCR token:count:{eventId}    # 입장 인원 증가
ZREM queue:{eventId} {userId} # 대기열에서 제거
```

**Lua Script (원자적 처리)**

```lua
local tokenKey = KEYS[1]           -- token:{eventId}:{userId}
local countKey = KEYS[2]           -- token:count:{eventId}
local queueKey = KEYS[3]           -- queue:{eventId}
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
    return 0  -- 대기열에 없음
end

-- 남은 자리 계산
local current = tonumber(redis.call('GET', countKey) or 0)
local remaining = maxConcurrent - current

-- 내 순번 < 남은 자리 → 토큰 발급
if rank < remaining then
    redis.call('SET', tokenKey, 1, 'EX', ttl)
    redis.call('INCR', countKey)
    redis.call('ZREM', queueKey, userId)
    return 1  -- 성공
end

return 0  -- 실패: 아직 내 차례 아님
```

### 4. Polling 시 TTL 갱신

```
EXPIRE token:{eventId}:{userId} 300
```

- 활동 중인 사용자는 TTL 계속 갱신
- 이탈 시 TTL 만료로 자동 반환

---

## 예매 상세 흐름

### 1. 잔여 좌석 확인

```
GET seat:count:{eventId}
```

- 0 이하면 즉시 "매진" 응답

### 2. 분산 락 획득

```java
RLock lock = redissonClient.getLock("lock:seat:" + eventId + ":" + seatId);

try {
    // waitTime: 3초, leaseTime: 5초
    boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);

    if (!acquired) {
        return "다른 사용자가 선택 중";
    }

    // 좌석 예매 처리

} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

### 3. DB 예매 처리

```java
// 1. 좌석 상태 더블 체크 (DB 기준)
Seat seat = seatRepository.findByIdForUpdate(seatId);
if (seat.isReserved()) {
    return "이미 예매된 좌석";
}

// 2. 예매 INSERT
Reservation reservation = new Reservation(userId, seatId, eventId);
reservationRepository.save(reservation);

// 3. 좌석 상태 변경
seat.reserve();
seatRepository.save(seat);

// 4. Redis 잔여 좌석 감소
redisTemplate.opsForValue().decrement("seat:count:" + eventId);
```

### 4. 토큰 반환

```
DEL token:{eventId}:{userId}
DECR token:count:{eventId}
```

**TTL 만료 시 처리**

토큰이 TTL로 자동 만료되면 `token:count`는 감소하지 않음 → 불일치 발생

**해결 방법:**
1. **Redis Keyspace Notification** - 키 만료 이벤트 구독하여 카운트 감소
2. **주기적 동기화** - Scheduler로 실제 토큰 수와 카운트 동기화
3. **만료 허용** - 약간의 불일치 허용, 최대 인원보다 적게 입장될 수 있음 (보수적)

