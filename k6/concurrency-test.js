import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';

export let errorRate = new Rate('booking_errors');
export let lockContentionCounter = new Counter('lock_contentions');
export let successfulBookingsCounter = new Counter('successful_bookings');

export let options = {
    stages: [
        { duration: '5s', target: 100 },
        { duration: '20s', target: 500 },
        { duration: '5s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'], // 95% of requests complete under 1s
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || 1;

// Standard RFC4122 v4 UUID generator (pure JS compatible with all k6 versions)
function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (Math.random() * 16) | 0,
            v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

export default function () {
    const jar = http.cookieJar();
    const userId = uuidv4();
    jar.set(BASE_URL, 'seatlock_user_id', userId);

    // ── Step 1: Query live seat map ──────────────────────────────────────────
    let mapRes = http.get(`${BASE_URL}/api/events/${EVENT_ID}/seats`);
    check(mapRes, {
        'seat map status is 200': (r) => r.status === 200,
    });

    // Pick a random seat ID from the 500-seat theater (IDs 1 to 500)
    const seatId = Math.floor(Math.random() * 500) + 1;
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    // ── Step 2: Attempt to lock the selected seat ────────────────────────────
    let lockRes = http.post(`${BASE_URL}/api/events/${EVENT_ID}/seats/${seatId}/lock`, null, params);

    check(lockRes, {
        'lock responded 200 (acquired) or 409 (contention)': (r) => r.status === 200 || r.status === 409,
    });

    if (lockRes.status === 409) {
        lockContentionCounter.add(1);
    } else if (lockRes.status === 200) {
        // ── Step 3: Book the locked seat with an Idempotency Key ──────────────
        const idempotencyKey = uuidv4();
        const bookPayload = JSON.stringify({
            seatId: seatId,
            idempotencyKey: idempotencyKey,
        });

        let bookRes = http.post(`${BASE_URL}/api/events/${EVENT_ID}/book`, bookPayload, params);

        check(bookRes, {
            'book responded 200 or 409': (r) => r.status === 200 || r.status === 409,
        });

        if (bookRes.status === 200) {
            successfulBookingsCounter.add(1);
        } else {
            errorRate.add(1);
        }
    }

    sleep(Math.random() * 0.5);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}

function textSummary(data, options) {
    let out = '\n======================================================\n';
    out += '📊 SeatLock k6 Concurrency Benchmark Summary\n';
    out += '======================================================\n';
    out += `Total HTTP Requests:       ${data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 'N/A'}\n`;
    out += `Successful Bookings:       ${data.metrics.successful_bookings ? data.metrics.successful_bookings.values.count : 0}\n`;
    out += `Lock Contention Events:    ${data.metrics.lock_contentions ? data.metrics.lock_contentions.values.count : 0}\n`;
    if (data.metrics.http_req_duration) {
        out += `p95 Latency:               ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)} ms\n`;
        out += `Avg Latency:               ${data.metrics.http_req_duration.values.avg.toFixed(2)} ms\n`;
    }
    out += '======================================================\n';
    return out;
}
