import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const reservationSuccess = new Counter('reservation_success');
const reservationFail = new Counter('reservation_fail');
const reservationRate = new Rate('reservation_success_rate');
const queueWaitTime = new Trend('queue_wait_time');

// 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const SEAT_COUNT = parseInt(__ENV.SEAT_COUNT) || 10000;

// 테스트 시나리오
// A 방식: WAS 1대 vs 3대 별도 테스트
export const options = {
    scenarios: {
        ticketing: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 500 },  // ramp-up
                { duration: '50s', target: 500 },  // 유지
                { duration: '10s', target: 0 },    // ramp-down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],  // 95% 요청이 5초 이내
        reservation_success_rate: ['rate>0.001'],  // 예매 성공
    },
};

// 테스트 데이터 (setup에서 생성)
let testData = {
    eventId: null,
    seatIds: [],
};

// Setup: 테스트 데이터 생성
export function setup() {
    const setupRes = http.post(`${BASE_URL}/api/test/setup?seatCount=${SEAT_COUNT}`);

    if (setupRes.status !== 200) {
        console.error('Setup failed:', setupRes.body);
        return null;
    }

    const data = JSON.parse(setupRes.body);
    console.log(`Setup complete: eventId=${data.eventId}, seats=${data.seatCount}`);

    return {
        eventId: data.eventId,
        seatIds: data.seatIds,
    };
}

// Main: 티켓팅 시나리오
export default function (data) {
    if (!data || !data.eventId) {
        console.error('No test data available');
        return;
    }

    const userId = crypto.randomUUID();
    const eventId = data.eventId;
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
    };

    // 1. 대기열 진입
    const enterRes = http.post(
        `${BASE_URL}/api/queue/enter?eventId=${eventId}`,
        null,
        { headers }
    );

    if (!check(enterRes, { 'queue enter success': (r) => r.status === 200 })) {
        return;
    }

    // 2. 상태 확인 (폴링)
    const startWait = Date.now();
    let status = 'WAITING';
    let attempts = 0;
    const maxAttempts = 30;

    while (status === 'WAITING' && attempts < maxAttempts) {
        const statusRes = http.get(
            `${BASE_URL}/api/queue/status?eventId=${eventId}`,
            { headers }
        );

        if (statusRes.status === 200) {
            const statusData = JSON.parse(statusRes.body);
            status = statusData.status;
        }

        if (status === 'WAITING') {
            sleep(0.5);  // 500ms 대기
            attempts++;
        }
    }

    queueWaitTime.add(Date.now() - startWait);

    if (status !== 'READY' && status !== 'ENTERED') {
        return;
    }

    // 3. 토큰 발급
    const tokenRes = http.post(
        `${BASE_URL}/api/queue/token?eventId=${eventId}`,
        null,
        { headers }
    );

    if (!check(tokenRes, { 'token acquired': (r) => r.status === 200 })) {
        return;
    }

    const tokenData = JSON.parse(tokenRes.body);
    if (!tokenData.success) {
        return;
    }

    // 4. 좌석 예매 (랜덤 좌석 선택)
    const randomSeatId = data.seatIds[Math.floor(Math.random() * data.seatIds.length)];

    const reserveRes = http.post(
        `${BASE_URL}/api/reservations`,
        JSON.stringify({
            eventId: eventId,
            seatId: randomSeatId,
        }),
        { headers }
    );

    const success = reserveRes.status === 200;

    if (success) {
        reservationSuccess.add(1);
        reservationRate.add(1);
    } else {
        reservationFail.add(1);
        reservationRate.add(0);
    }

    sleep(0.1);
}

// Teardown: 정리
export function teardown(data) {
    const cleanupRes = http.del(`${BASE_URL}/api/test/cleanup`);
    console.log('Cleanup:', cleanupRes.status === 200 ? 'success' : 'failed');
}
