# 시스템 한계점 및 개선 방안

현재 구현의 한계점과 실제 운영 환경에서의 개선 방안 정리.

---

## 1. Redis-DB 트랜잭션 불일치

### 현재 상황

```java
@Transactional  // DB만 관리
public ReservationResponse reserve(...) {
    seat.reserve();                    // DB 트랜잭션 O
    reservationRepository.save(...);   // DB 트랜잭션 O

    redisTemplate.decrement(...);      // Redis - 트랜잭션 X
    queueService.releaseToken(...);    // Redis - 트랜잭션 X
}
```

- `@Transactional`은 DB 트랜잭션만 관리
- Redis 작업은 별도로 실행됨

### 현재 상황에서 괜찮은 이유

| 실패 지점 | 영향 | 괜찮은 이유 |
|-----------|------|------------|
| 잔여좌석 감소 | Redis 카운트 불일치 | DB가 실제 기준, Redis는 캐시 |
| 토큰 반환 | 다음 대기자 입장 지연 | TTL 만료로 자동 복구 (최대 5분) |

- Redis 실패 자체가 드묾
- 잔여좌석 불일치 시에도 실제 예매는 DB에서 검증
- 토큰은 TTL이 있어 자연 복구됨

### 잠재적 문제

- 잔여좌석 불일치가 누적되면 "매진인데 남은 것처럼" 표시될 수 있음
- 토큰 반환 실패 시 대기자 입장이 최대 5분 지연

### 개선 방안

**1. 이벤트 기반 + 재시도 (Saga 패턴)**
```java
@TransactionalEventListener(phase = AFTER_COMMIT)
@Retryable(maxAttempts = 3)
public void handleReservationCreated(ReservationCreatedEvent event) {
    redisTemplate.decrement(seatCountKey);
    queueService.releaseToken(event.eventId(), event.userId());
}
```

**2. 주기적 동기화**
```java
@Scheduled(fixedRate = 60000)
public void syncSeatCount() {
    long count = seatRepository.countAvailable(eventId);
    redisTemplate.set(seatCountKey, count);
}
```

---

## 2. 분산 락 leaseTime 초과

### 현재 상황

```java
lock.tryLock(3, 5, TimeUnit.SECONDS);
//              ↑ leaseTime = 5초
```

### 현재 상황에서 괜찮은 이유

- 예매 처리 (DB 조회 + 저장 + Redis 업데이트)가 5초 내 완료됨
- 일반적인 상황에서 1초 이내 처리

### 잠재적 문제

작업이 5초 초과 시:
1. 락 자동 해제
2. 다른 요청이 락 획득
3. 동시 예매 가능성 (Overselling)

발생 가능 상황:
- DB 슬로우 쿼리
- 네트워크 지연
- GC pause

### 개선 방안

**1. leaseTime 충분히 설정**
```java
lock.tryLock(3, 30, TimeUnit.SECONDS);  // 30초
```

**2. Redisson Watchdog 사용**
```java
lock.lock();  // leaseTime 미지정 → Watchdog 활성화
// 30초마다 자동 연장, unlock() 호출 시까지 유지
```

---

## 3. Redis 단일 장애점 (SPOF)

### 현재 상황

- 단일 Redis 인스턴스 사용
- 대기열, 토큰, 분산 락 모두 Redis 의존

### 현재 상황에서 괜찮은 이유

- 로컬/개발 환경에서는 충분
- Redis 자체 안정성이 높음

### 잠재적 문제

Redis 다운 시:
- 대기열 진입 불가
- 토큰 발급 불가
- 분산 락 불가
- **전체 시스템 마비**

### 개선 방안

**1. Redis Cluster / Sentinel**
- 고가용성 구성
- 자동 Failover

**2. Fallback 전략**
```java
try {
    redisTemplate.opsForZSet().add(...);
} catch (RedisConnectionException e) {
    // DB 기반 대기열로 Fallback
    // 또는 서킷 브레이커로 빠른 실패
}
```

---

## 정리

| 한계점 | 현재 괜찮은 이유 | 개선 방안 |
|--------|-----------------|----------|
| Redis-DB 불일치 | TTL 자동 복구, DB가 기준 | 이벤트 기반 재시도, 주기적 동기화 |
| 락 시간 초과 | 처리 시간 1초 이내 | Watchdog, leaseTime 조정 |
| Redis SPOF | 개발 환경, Redis 안정성 | Cluster / Sentinel, Fallback |
