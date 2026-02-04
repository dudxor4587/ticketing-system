# 기능적 요구사항

선착순 티켓팅 시스템의 핵심 기능 정의. (백엔드 API만)

---

## 1. 대기열 (Queue)

### 1.1 대기열 진입
- 티켓팅 시작 시 대기열 진입
- 대기 순번 부여 (입장 시간 기준)

### 1.2 대기 상태 조회 (Polling)
- 현재 대기 상태 (waiting / ready / entered)
- 내 앞 대기 인원 수

### 1.3 입장
- ready 상태에서 입장 토큰 획득 시도
- 토큰 TTL + Polling 시 갱신
- 이탈 시 자동 토큰 반환 (TTL 만료)

---

## 2. 좌석 (Seat)

### 2.1 좌석 조회
- 이벤트의 전체 좌석 목록 조회
- 좌석 상태 (AVAILABLE / RESERVED)

---

## 3. 예매 (Reservation)

### 3.1 예매 요청
- 좌석 선택 + 예매 완료를 한번에 처리
- 분산 락으로 동시 선택 방지
- 성공 시 좌석 상태 RESERVED로 변경

---

## API 목록

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/queue/enter | 대기열 진입 |
| GET | /api/queue/status | 대기 상태 조회 (Polling) |
| POST | /api/queue/token | 입장 토큰 획득 |
| GET | /api/events/{eventId}/seats | 좌석 목록 조회 |
| POST | /api/reservations | 예매 요청 |

---

## 사용자 식별

인증 없이 헤더로 사용자 구분:

```
X-User-Id: user123
```

---

## 테스트 데이터

이벤트, 좌석은 테스트용으로 미리 생성:

```sql
-- 이벤트
INSERT INTO event (id, name, ticket_open_time, max_concurrent)
VALUES (1, '테스트 콘서트', '2024-01-01 12:00:00', 800);

-- 좌석 (100석)
INSERT INTO seat (id, event_id, section, row, number, status)
SELECT
    generate_series(1, 100),
    1,
    'A',
    (generate_series(1, 100) - 1) / 10 + 1,
    (generate_series(1, 100) - 1) % 10 + 1,
    'AVAILABLE';
```

---

## 비기능적 요구사항

### 성능
- 동시 접속 10,000명 처리
- p99 응답 시간 500ms 이내
- Overselling 0건

### 동시성
- 같은 좌석 동시 선택 시 1명만 성공
- 분산 환경에서도 정합성 보장
